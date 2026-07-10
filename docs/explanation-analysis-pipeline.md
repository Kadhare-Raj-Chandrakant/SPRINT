# Explanation: Analysis Pipeline

This document explains *how* a pull request becomes a triage tier — the
heuristics engine, the LLM review, how they're merged, and why the bot behaves
the way it does. For the wiring, see [ARCHITECTURE.md](../ARCHITECTURE.md); for
config keys, [reference: configuration](reference-configuration.md).

## Overview

```
fetch diff → parse chunks ─┬─▶ HeuristicsAnalysisEngine ─┐
                           │                             ├─▶ mergeAndRank ─▶ computeTier ─▶ publish
                           └─▶ LLMReviewEngine ──────────┘
```

Both engines run over the same set of `ChangeChunk`s (parsed from the unified
diff). They run independently; a failure in one does not block the other.

## Step 1 — Fetch & parse

`GitHubApiClient.fetchDiff` pulls the unified diff and `UnifiedDiffParser`
splits it into `ChangeChunk`s. Chunking bounds cost and latency:

- files larger than `800_000` bytes are skipped (not chunked),
- chunk size kept within `200`–`9000` chars,
- at most `60` chunks per PR (truncated beyond that).

Each `ChangeChunk` carries the file path, the hunk lines, and (where detectable)
line numbers — line numbers power inline comments later.

## Step 2 — Heuristics (cheap, free, sync)

`HeuristicsAnalysisEngine` runs six `Rule` implementations **in parallel** (a
fixed 6-thread pool). Each rule produces zero or more `Finding`s. Rules:

| Rule | Looks for |
|---|---|
| `SecretsDetectionRule` | `AWS_KEY`, `PRIVATE_KEY`, `PASSWORD`, `API_KEY`, `GITHUB_TOKEN`, `SLACK_TOKEN` patterns in added lines. |
| `CommitMessageStyleRule` | Non-conventional / low-effort commit messages. |
| `DiffShapeRule` | Suspicious diff shape (e.g. huge single-file dump, mixed concerns). |
| `AccountAgeRule` | New/throwaway author account. |
| `CommentCodeRatioRule` | Low comment-to-code ratio (copy-paste signature). |
| `BoilerplatePhraseRule` | Boilerplate / placeholder text indicating AI-generated or stubbed work. |

Each `Finding` has: `id`, `filePath`, `lineNumber` (nullable), `severity`,
`category` (e.g. `AI_LIKELIHOOD`, `SECURITY`), `message`, and optional `details`.
Every `Rule` declares a `category()` and a `precedenceScore()` used later for
ranking.

Heuristics are deterministic and cost nothing, so they always run (when
`app.heuristics-enabled`) even if the LLM is down.

## Step 3 — LLM review (expensive, async-tolerant)

`LLMReviewEngine` sends each `ChangeChunk` to the LLM (configured provider) with
a prompt asking for a review in structured JSON: a list of findings (file, line,
severity, category, message) plus a short narrative. The chunk narrative is
passed to `SummaryGenerator` to build the human-readable summary.

### LLM fallback

`LLMClient` is the interface; `OpenAiCompatibleClient` is the only implementation
(OpenAI `/chat/completions` shape). `LLMFallbackChain` wraps a **list** of
providers (`llm.providers`):

- It calls the first provider; on a successful, strictly-validated response it
  returns.
- On failure (network/HTTP/parse/`LLMResponseException`) it tries the next.
- If **all** providers fail, the engine returns no LLM findings and the review
  proceeds **heuristics-only** — the PR is still triaged, just without AI
  commentary.

This is why a misconfigured LLM never breaks triage: you lose AI signal, not the
whole bot.

## Step 4 — Merge & rank

`FindingMerger.mergeAndRank` collects heuristics + LLM findings, dedupes
overlapping ones (same file/line/category), and orders them by
`precedenceScore` (highest first). The merged list feeds the summary and the
inline comments.

## Step 5 — Tier & suggested action

`SummaryGenerator.computeTier` maps the merged findings to a `TriageResult`:

| Tier | Meaning |
|---|---|
| 🟢 **GREEN** | Looks legitimate / human-authored. Suggested action: `REVIEW_AND_MERGE`. |
| 🟡 **YELLOW** | Some risk signals; worth a human glance. Suggested action: `MANUAL_CHECK`. |
| 🔴 **RED** | Strong AI/low-effort/throwaway signals. Suggested action: `CONSIDER_CLOSING`. |

A separate **`security` flag** is raised whenever any `SECURITY` finding exists
(usually from `SecretsDetectionRule`). Security is an *orthogonal axis*: a PR can
be GREEN-but-security-flagged, or RED-without-secrets. The flag drives labels and
immediate email alerts regardless of tier.

Intuition: **RED ≈ "this looks AI-generated / low-effort"**, not "this is
dangerous." Dangerous content is the `security` flag.

## Step 6 — Publish

`ReviewPublisher.publishReview`:

- builds **inline comments** for findings that have a line number (when
  `app.inline-comments`),
- posts the markdown **summary review**. It sends `APPROVE` **only** when there
  are *no findings* **and** `app.auto-approve` is true; otherwise it submits a
  `COMMENT` review,
- applies `triage:green|yellow|red` (and `security`) labels when `labelsEnabled`.

The human always makes the merge/close decision — via the GitHub UI or the
dashboard's one-click `Approve / Request changes / Close` buttons.

## Step 7 — Persist & notify

- `PrAnalysis` is saved (tier, security flag, summary, findings JSON, status).
- `ThresholdAlertService.maybeAlert` sends an immediate email when the tier ≥ the
  installation's `thresholdTier` (default `RED`) **or** the security flag is set.
  Alerts are deduped via the `alerted` column so re-deliveries don't spam.
- `DigestService` (scheduled cron) groups new analyses per installation since the
  last digest (`Meta.lastDigestAt`) and emails a digest to maintainers.

## Why this design

- **Cheap-first.** Heuristics catch the obvious (secrets, boilerplate) for free
  and synchronously; the LLM is the expensive path and degrades gracefully.
- **Two signals, one decision.** Heuristics give deterministic coverage; the LLM
  adds nuance. Merging them avoids double-reporting.
- **Human-in-the-loop.** Tiers *suggest*; the bot never merges. Auto-`APPROVE`
  only fires when there is genuinely nothing to flag.
- **Resilient.** Signature check, dedupe by commit SHA, single-use action tokens,
  LLM fallback, and graceful shutdown all keep the system safe under pressure.
