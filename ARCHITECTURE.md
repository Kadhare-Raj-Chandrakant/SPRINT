# Architecture

This document describes how the PR Triage Bot (`SPRINT`) is structured and how a
pull request flows through it. It is the companion to the
[API reference](docs/reference-api.md) and the
[analysis pipeline explanation](docs/explanation-analysis-pipeline.md).

## Design in one paragraph

A single Spring Boot service receives GitHub `pull_request` webhooks, verifies
their HMAC signature, and enqueues an asynchronous review job. The job fetches
the PR diff, runs **heuristics** and an **LLM review** over the changed chunks,
merges and ranks the findings, computes a triage tier, posts a review comment
(and labels), persists the result, and optionally emails the maintainer. It is
server-side and stateless except for a PostgreSQL store used for dedupe,
reporting, one-click actions, and email state.

## Component map

```
GitHub ──webhook──▶ GitHubWebhookController ──▶ ReviewOrchestrator (@Async)
                        │  (verifies HMAC)            │
                        │                            fetch diff
                        ▼                            ▼
                 WebhookSignatureVerifier    GitHubApiClient (JWT→install token)
                                                    │
                                    ┌───────────────┼───────────────┐
                                    ▼               ▼               ▼
                          HeuristicsAnalysisEngine   LLMReviewEngine   (parallel)
                          (6 Rule impls)             (LLMFallbackChain)
                                    └───────────────┼───────────────┘
                                                    ▼
                                          FindingMerger.mergeAndRank
                                                    ▼
                                   SummaryGenerator.computeTier → TriageResult
                                                    ▼
                                         ReviewPublisher.publishReview
                                          ├─ GitHubApiClient.submitReview (+labels)
                                          └─ persist → PrAnalysis
                                                    └─ ThresholdAlertService.maybeAlert
                                                        └─ MailService (digest/alert)

Web UI:  DashboardController (/)  ·  ActionController (/action)  ·  TokenService (signed tokens)
```

| Package | Responsibility |
|---|---|
| `webhook` | Receive + authenticate webhooks; route PR actions. |
| `service` | `ReviewOrchestrator` — the end-to-end pipeline orchestration. |
| `analysis` | Heuristic rules, LLM review, `SummaryGenerator` (tier + summary). |
| `analysis.heuristics` | The six `Rule` implementations. |
| `llm` | `LLMClient` interface, `OpenAiCompatibleClient`, `LLMFallbackChain`. |
| `github` | `GitHubApiClient` (REST + diff), `GitHubJwtGenerator` (App JWT → install token). |
| `engine` | `FindingMerger` (dedupe/rank), `ReviewPublisher` (post comment + labels). |
| `actions` | `TokenService` — HMAC-signed, single-use action tokens. |
| `web` | `DashboardController` (read-only UI), `ActionController` (one-click actions). |
| `email` | `MailService`, `DigestService` (scheduled), `ThresholdAlertService` (immediate). |
| `config` | `*Properties` bindings, `ConfigService` (per-installation resolution). |
| `persistence` | `PrAnalysis`, `MaintainerConfig`, `Meta` + repositories. |
| `domain` | `Finding`, `ChangeChunk`, `PullRequestContext`, `TriageResult`, `ReviewComment`. |

## Data flow (one PR)

1. **Ingest** — `GitHubWebhookController.handleGitHubWebhook` verifies
   `X-Hub-Signature-256` and confirms `event == pull_request` with
   `action ∈ {opened, synchronize, reopened}`; calls
   `ReviewOrchestrator.processPullRequest` asynchronously (`@Async`).
2. **Context** — `GitHubApiClient.fetchPullRequestContext` parses owner/repo/PR
   number/title/description/author/commit SHA/installation id from the payload
   (no API call yet).
3. **Dedupe** — if a `PrAnalysis` exists for `owner/repo/pr/commitSha`, skip
   (debounces `synchronize` force-push storms).
4. **Fetch diff** — `GitHubApiClient.fetchDiff` exchanges the App JWT for an
   installation token (cached 55 min) and fetches the unified diff.
5. **Parse** — `UnifiedDiffParser` splits the diff into `ChangeChunk`s.
6. **Analyze** (see [pipeline explanation](docs/explanation-analysis-pipeline.md)):
   - Heuristics (if `app.heuristics-enabled`) — 6 rules in a 6-thread pool.
   - LLM (if `app.llm-enabled`) — per-chunk review; on failure, continues with
     heuristics only.
7. **Merge & rank** — `FindingMerger.mergeAndRank` dedupes and orders by
   `precedenceScore`.
8. **Tier** — `SummaryGenerator.computeTier` produces a `TriageResult`
   (GREEN/YELLOW/RED + `securityFlag` + suggested action).
9. **Publish** — `ReviewPublisher.publishReview`:
   - builds inline comments for findings with a line number,
   - posts a review (`APPROVE` only when no findings **and** `auto-approve`),
   - applies `triage:*` (+ `security`) labels when enabled.
10. **Persist & alert** — save `PrAnalysis`; `ThresholdAlertService.maybeAlert`
    sends an immediate email for RED/security PRs (deduped via `alerted`).
11. **Email** — `DigestService` (scheduled cron) groups new analyses per
    installation and sends a digest since the last run.

## Key design decisions

- **Webhook, not polling.** Real-time on PR open; works without a browser open.
  (The SRS originally explored a browser extension and dismissed it — see
  `docs/SRS.md` §3.)
- **Human-in-the-loop.** Tiers *suggest* an action; the bot never auto-merges.
  One-click `close` sets `state=closed` but never merges (see
  `ActionController`).
- **Security is a separate axis.** A `security` flag (from `SECURITY`
  findings) can attach to any tier; RED is about AI/low-effort likelihood, not
  danger.
- **Provider fallback.** `LLMFallbackChain` tries providers in config order;
  all providers use one `OpenAiCompatibleClient` (OpenAI-compatible REST shape).
- **Cheap-first.** Heuristics run synchronously and free; the LLM call is the
  expensive path and degrades gracefully (heuristics-only) on failure.
- **Per-installation config.** `ConfigService.resolve(installationId)` overlays
  `MaintainerConfig` (digest cron, threshold tier, labels, email, actions,
  recipients) on top of global `application.yaml` / env defaults.
- **Idempotent + resumable.** Dedupe by commit SHA; graceful shutdown lets
  in-flight reviews finish (`spring.lifecycle.timeout-per-shutdown-phase: 30s`).

## Storage

PostgreSQL via Spring Data JPA (`ddl-auto: update`). Tables:

- `pr_analysis` — one row per analysis (tier, security flag, summary, findings
  JSON, status `COMPLETED`/`ACTIONED`, `alerted`, `action_taken`).
- `maintainer_config` — per-installation overrides.
- `meta` — key/value (e.g. `lastDigestAt`).

## Health & ops

- `GET /webhook/health` → `OK` (liveness for the load balancer).
- Actuator: `health`, `info`, `prometheus`, `metrics` (exposes
  `github-api` and `llm` custom health indicators).
- Structured JSON logging (Logstash encoder); `traceId` = `X-GitHub-Delivery`
  for request correlation across async threads.
