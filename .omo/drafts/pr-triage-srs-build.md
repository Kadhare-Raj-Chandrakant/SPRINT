---
slug: pr-triage-srs-build
status: approved
intent: clear
review_required: false
pending-action: write .omo/plans/pr-triage-srs-build.md
approach: Harden the existing Java Spring Boot bot and fill every SRS gap (persistence, digest email, one-click action links, labels, tiering, dashboard) without rewriting. LLM = fallback chain OpenAI-compatible → NVIDIA NIM → Ollama (no Claude, per user override).
---

# Draft: pr-triage-srs-build

## Components (topology ledger)
| id | outcome (one line) | status | evidence |
| --- | --- | --- | --- |
| webhook | Verified GitHub webhook ingestion + HMAC | active | GitHubWebhookController.java, WebhookSignatureVerifier.java |
| github | App JWT → installation token + diff/comment/review API | active | GitHubApiClient.java, GitHubJwtGenerator.java |
| heuristics | 6 cheap heuristic rules | active | analysis/heuristics/*.java |
| llm | Per-chunk LLM review (fallback chain) | active (refactor) | LLMReviewEngine.java, llm/*.java |
| summary | SRS §6 structured summary + tier/action | active (fix) | SummaryGenerator.java, SignalAggregator (new) |
| persistence | Postgres PR analysis + dedupe + action history | deferred→build | PrAnalysis (new) |
| email | Digest + threshold email via Spring Mail | build | DigestService (new) |
| actions | Signed one-click action links + handler | build | TokenService (new), ActionController (new) |
| dashboard | Read-only Thymeleaf dashboard | build | DashboardController (new) |

## Open assumptions (announced defaults)
| assumption | adopted default | rationale | reversible? |
| --- | --- | --- | --- |
| App name inconsistency (`glint` in yaml) | rename to `pr-triage` | matches project dir + SRS | yes |
| `reviewSummaryEnabled` dead config | remove | unused, clarifies config | yes |
| Dashboard tech | Thymeleaf server-rendered | no new stack, reuses data | yes |
| Per-chunk LLM kept (not collapsed to 1 call) | keep, add aggregate summary call | matches SRS §6 for summary; inline findings preserved | yes |
| DB migrations | Hibernate `ddl-auto=update` (no Flyway) | single small stable schema; skip migration framework | yes |
| Action token secret | `app.action-secret` env (HMAC) | SRS §9 signed/expiring/single-use | yes |

## Findings (cited - path:lines)
- Scrap code DOES NOT COMPILE: `SummaryGenerator.java:179` calls `prContext.getFilesChanged()` and `SummaryGeneratorTest.java:26,58,89` calls `prContext.setFilesChanged(...)`, but `PullRequestContext.java:12-23` has NO `filesChanged` field. Whole project fails to build.
- AI-likelihood/risk axis conflation (bug): `LLMReviewEngine.java:224-235` tags HIGH AI-likelihood finding as `severity("CRITICAL")`, so `SummaryGenerator.extractRiskLevel()` (`SummaryGenerator.java:109-127`) classifies the PR as CRITICAL risk purely because an LLM said "likely AI" — exactly the axis-mixing SRS §5 warns against ("Security risk is a different axis from AI-likelihood").
- No email at all: no `spring-boot-starter-mail` in `pom.xml:33-136`; `ReviewPublisher.java` only posts a GitHub review. Violates SRS §7/§11 (digest email is the core value).
- No persistence: no JPA/DB deps in `pom.xml`; `ReviewOrchestrator` is stateless. No dedupe on `synchronize` (SRS §9 debounce), no action history for links (SRS §8), re-analyzes unchanged PRs (SRS §10).
- No one-click action links / Action Handler: no endpoint or token service exists. Violates SRS §7.3/§8/§9.
- No PR labels: `ReviewPublisher.java:38-51` only submits a review event (APPROVE/COMMENT); never applies `ai-suspected`/`needs-review`/`low-effort` (SRS §7.4).
- Tier/confidence-band framing incomplete: `SummaryGenerator.determineRecommendation()` (`:203-215`) maps AI+risk to a sentence but the explicit 🟢/🟡/🔴 bands (SRS §5 table) + suggested action are not surfaced as a first-class signal, and the separate ⚠️ security flag (SRS §5) is not distinct from the tier.
- LLM provider mismatch: SRS recommends Claude; code uses Ollama/NIM (`LLMProperties.java:22,25`, `application.yaml:77-78`). User override: fallback chain OpenAI-compatible → NVIDIA NIM → Ollama, no Claude.
- No repo-context / linked-issue fetch: `GitHubApiClient.fetchDiff()` (`:147`) fetches only the diff; SRS §4 wants README + CONTRIBUTING + linked issue for LLM context.
- `application.yaml:7` `spring.application.name: glint` — inconsistent with project name.
- `AppProperties.reviewSummaryEnabled` (`:15`) is unused dead config.
- `ReviewOrchestrator.publishReviewWithFindings()` (`:123`) regenerates the summary a 2nd time (redundant with `:105`).
- App name in `application.yaml` = `glint`; tests exist and are reasonable (`bot/src/test/...`): GitHubWebhookControllerTest, WebhookSignatureVerifierTest, ReviewOrchestratorTest, ReviewPublisherTest, FindingMergerTest, UnifiedDiffParserTest, HeuristicsAnalysisEngineTest, SummaryGeneratorTest, LLMReviewEngineTest, CommitMessageStyleRuleTest, DiffShapeRuleTest, BotApplicationTests.

## Decisions (with rationale)
1. **Keep & harden the Java Spring Boot bot** (user): salvage webhook/heuristics/LLM/parser/publisher; fill SRS gaps. Fastest path, nothing discarded.
2. **LLM fallback chain = OpenAI-compatible → NVIDIA NIM → Ollama, no Claude** (user override of SRS §8): add `OpenAiCompatibleClient` (covers Ollama's `/v1` + OpenAI); keep NvidiaNimClient + OllamaClient; `LLMFallbackChain` tries in configured order. Order: openai-compatible, nvidia-nim, ollama.
3. **Pluggable SMTP via Spring Mail** (user): `JavaMailSender` over any provider (SES/Postmark/SendGrid/Mailtrap) through one config surface — matches SRS §8, provider-agnostic.
4. **Full SRS (Phase 1-3)** (user): includes persistence, dedupe, labels, digest email, threshold alerts, one-click signed action links + Action Handler, per-maintainer config, and a read-only dashboard.
5. Tiering per SRS §5: GREEN=review&merge, YELLOW=manual check, RED=consider closing (one-click bulk close, human click only — never auto). Security flag is a SEPARATE boolean axis that can attach to any tier.

## Scope IN
- Fix build + AI/risk axis bug; add `filesChanged` + `SignalAggregator` tier.
- LLM fallback chain with new OpenAI-compatible client.
- Postgres persistence (PrAnalysis) + `synchronize` dedupe by commitSha + action history.
- PR labels from tier (ai-suspected / needs-review / low-effort).
- Digest email (configurable cron) + threshold immediate email, via Spring Mail.
- Signed, expiring, single-use action tokens + Action Handler (approve / request-changes / close).
- Per-installation maintainer config (cadence, thresholds, toggles).
- Read-only Thymeleaf dashboard reusing analysis + action links.
- Repo-context (README/CONTRIBUTING) + linked-issue fetch for richer LLM context (SRS §4).

## Scope OUT (Must NOT have)
- Full auto-merge / auto-reject (SRS non-goal; human stays in loop; RED is one-click, not automatic).
- Rewriting in Node/Probot or Python/FastAPI (user chose Java).
- Claude API integration (user override).
- Deep static-analysis / security scanning engine beyond the existing secrets heuristic (SRS non-goal).
- Hosted multi-tenant billing/pricing (track cost per installation only, SRS §10, as a stored metric — no billing UI).

## Open questions
- None blocking; all forks resolved by the 4 user decisions above.

## Approval gate
status: approved
approach: Harden Java bot + fill full SRS. LLM=OpenAI-compatible→NIM→Ollama. Email=Spring Mail. Scope=Full SRS.
workflow: git init on `main`; feature branches per change; merge to `main` after verified; no Co-Authored-By; compact at ~70% context.
