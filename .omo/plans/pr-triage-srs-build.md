# pr-triage-srs-build - Work Plan

## TL;DR (For humans)
<!-- Fill this LAST, after the detailed plan below is written, so it summarizes the REAL plan. -->
<!-- Plain English for a non-engineer: NO file paths, NO todo numbers, NO wave/agent/tool names. -->

**What you'll get:** A working PR-triage bot that reads pull requests, decides how likely each is AI-generated and how risky, labels them, emails you one daily digest (plus instant alerts for risky ones), and lets you approve / request-changes / close straight from an email or a simple web page with one click — and it remembers what it already reviewed so it doesn't repeat itself.

**Why this approach:** We keep the bot we already have (it works for reading GitHub) and fill in only what's missing, rather than rewriting it. For the AI check we use whichever model provider you point at (OpenAI-style, NVIDIA, or Ollama) in that order, not a single locked-in service.

**What it will NOT do:** It will not auto-merge or auto-reject (you always click); it will not be rewritten in another language; it will not use the Claude service; it will not become a paid multi-tenant product.

**Effort:** XL
**Risk:** Medium - multi-phase build on a currently-broken codebase; each phase is independently verifiable.
**Decisions to sanity-check:** LLM = OpenAI-compatible → NVIDIA NIM → Ollama (no Claude); email via standard SMTP; full SRS scope; keep the existing Java bot.

Your next move: approve, or run a high-accuracy review. Full execution detail follows below.

---

> TL;DR (machine): Effort XL, Risk Medium. Harden Java bot + fill full SRS (persistence, labels, digest email, signed one-click action links + handler, per-maintainer config, dashboard). LLM fallback OpenAI-compatible→NIM→Ollama.

## Scope
### Must have
- Fix the current build break + the AI-likelihood/risk axis bug.
- First-class tier 🟢/🟡/🔴 + separate ⚠️ security flag + suggested action (SRS §5).
- LLM fallback chain: OpenAI-compatible → NVIDIA NIM → Ollama (new OpenAI-compatible client); no Claude.
- Postgres persistence (`PrAnalysis`) + `synchronize` dedupe by commitSha + action history.
- PR labels from tier (`ai-suspected` / `needs-review` / `low-effort`).
- Digest email (configurable cron) + threshold immediate email via Spring Mail.
- Signed, expiring, single-use action tokens + Action Handler (approve / request-changes / close).
- Per-installation maintainer config (cadence, thresholds, toggles).
- Read-only Thymeleaf dashboard reusing analyses + action links.
- Repo-context (README/CONTRIBUTING) + linked-issue fetch for richer LLM context (SRS §4).

### Must NOT have (guardrails, anti-slop, scope boundaries)
- No auto-merge / auto-reject. Human stays in the loop; RED is one-click-bulk-close (human click only).
- No rewrite in Node/Probot or Python/FastAPI (user chose Java).
- No Claude API integration.
- No deep static-analysis / security-scanning engine beyond the existing secrets heuristic (SRS non-goal).
- No hosted billing/pricing; cost-per-installation is a stored metric only (SRS §10), no billing UI.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD for net-new logic (SignalAggregator, TokenService, dedupe, fallback chain, label mapping); tests-after acceptable for wiring. Framework: JUnit 5 + Spring Boot Test + Testcontainers (Postgres) + MockMvc for controller.
- Evidence: `.omo/evidence/task-<N>-pr-triage-srs-build.<ext>` (test output, curl responses, screenshots).

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. Fewer than 3 (except the final) means you under-split.

- **Wave 0 (repo setup):** T0
- **Wave 1 (Phase 0 — build + core):** T1, T2, T3, T4, T5, T6
- **Wave 2 (Phase 1 — persistence + labels):** T7, T8, T9, T10, T11
- **Wave 3 (Phase 2 — email):** T12, T13, T14, T15
- **Wave 4 (Phase 3 — action links):** T16, T17, T18, T19
- **Wave 5 (Phase 4 — multi-repo config):** T20, T21
- **Wave 6 (Phase 5 — dashboard):** T22
- **Final verification wave:** F1–F4

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| T1 | - | T3, T7, T9, T11 | T2, T4, T5, T6 |
| T2 | - | T3 | T1, T4, T5, T6 |
| T3 | T1, T2 | T8, T9, T13, T14 | T4, T5, T6 |
| T4 | - | T3 (LLMClient bean) | T1, T2, T5, T6 |
| T5 | - | T6, T10 | T1, T2, T3, T4 |
| T6 | T5 | T7, T9 | T1, T2, T3, T4 |
| T7 | T1, T5, T6 | T9 | T8 |
| T8 | T3 | - | T7 |
| T9 | T3, T6, T7 | T13 | T8 |
| T10 | T5 | T12, T13 | T7, T8, T9 |
| T11 | T9, T10 | - | T12 |
| T12 | T3, T10 | T14 | T11 |
| T13 | T11, T16 | T19 | T12 |
| T14 | T3, T9 | T13, T16, T22 | - |
| T15 | T14, T17 | T18 | - |
| T16 | T14 | T13, T22 | T15 |
| T17 | T14, T15 | T18 | T16 |
| T18 | T15, T16, T17 | T19 | - |
| T19 | T13, T14, T18 | - | - |
| T20 | T9, T11, T12 | T21 | - |
| T21 | T20 | - | - |
| T22 | T6, T14, T16 | - | - |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE WITH edit/apply_patch - never rewrite the headers above. -->
- [ ] 0. Initialize git repo (default branch `main`) + branch strategy
  What to do / Must NOT do: In `/home/grim/Projects/pr-triage` run `git init -b main` (fallback: `git init` then `git checkout -b main` + `git branch -m main`). Add `.gitignore` (ignore `target/`, `.env`, `*.log`; KEEP `.omo/` tracked as plan artifacts; KEEP `docs/` + `bot/`). Create the initial commit on `main` capturing the existing project (SRS + scrap code). All subsequent work happens on feature branches (`fix/...`, `feat/...`, `refactor/...`); merge to `main` ONLY after that wave's tests pass. NEVER add a `Co-Authored-By` trailer to any commit. Do NOT push to a remote unless explicitly asked.
  Parallelization: Wave 0 | Blocked by: - | Blocks: (precondition for all work)
  References: /home/grim/Projects/pr-triage (currently NOT a git repo)
  Acceptance criteria: `git rev-parse --abbrev-ref HEAD` == `main`; `.gitignore` present; initial commit exists; `git status` clean.
  QA scenarios: happy = repo on main + baseline commit; failure = head not `main` → fix before any feature branch. Evidence `.omo/evidence/task-0-pr-triage-srs-build.log`
  Commit: Y | chore: initialize repo on main (no Co-Authored-By)

- [ ] 1. Add `filesChanged` to `PullRequestContext` (fix compile break)
  What to do / Must NOT do: Add `private List<String> filesChanged;` (Lombok `@Data` supplies getter/setter) to `bot/src/main/java/com/bot/bot/domain/PullRequestContext.java`. In `ReviewOrchestrator.processPullRequestContext` after `diffParser.parse(chunks)` set `prContext.setFilesChanged(chunks.stream().map(ChangeChunk::getFilePath).distinct().collect(toList()))`. Do NOT change the field name used by `SummaryGenerator.java:179`.
  Parallelization: Wave 1 | Blocked by: - | Blocks: T3, T7, T9, T11
  References: bot/src/main/java/com/bot/bot/domain/PullRequestContext.java:12-23; bot/src/main/java/com/bot/bot/service/ReviewOrchestrator.java (diffParser.parse call); bot/src/main/java/com/bot/bot/analysis/SummaryGenerator.java:179; bot/src/test/java/com/bot/bot/analysis/SummaryGeneratorTest.java:26,58,89
  Acceptance criteria: `./mvnw -q -pl bot compile` succeeds AND `./mvnw -q -pl bot test -Dtest=SummaryGeneratorTest` passes (getFilesChanged/setFilesChanged now resolve).
  QA scenarios: happy = orchestrator sets 3 distinct files from 3 chunks; failure = null list when no chunks (assert empty list, not NPE). Evidence `.omo/evidence/task-1-pr-triage-srs-build.log`
  Commit: Y | fix(domain): add filesChanged to PullRequestContext to unblock build

- [ ] 2. Fix AI-likelihood↔risk axis conflation in `LLMReviewEngine`
  What to do / Must NOT do: In `bot/src/main/java/com/bot/bot/analysis/LLMReviewEngine.java:224-235`, stop tagging HIGH AI-likelihood findings as `CRITICAL`. Map AI_LIKELIHOOD severity by confidence: HIGH→`INFO`, MEDIUM→`INFO`, LOW→`INFO` (it is a detection signal, never a risk). Keep `category(AI_LIKELIHOOD)` + `aiGeneratedLikelihood`. Do NOT change how CODE_REVIEW/SECURITY findings get severity. Add regression test asserting a HIGH-AI finding serializes with severity INFO.
  Parallelization: Wave 1 | Blocked by: - | Blocks: T3
  References: bot/src/main/java/com/bot/bot/analysis/LLMReviewEngine.java:224-235; bot/src/main/java/com/bot/bot/analysis/SummaryGenerator.java:109-127 (extractRiskLevel)
  Acceptance criteria: `./mvnw -q -pl bot test -Dtest=LLMReviewEngineTest` passes AND grep of produced Finding JSON shows `"category":"AI_LIKELIHOOD","severity":"INFO"`.
  QA scenarios: happy = HIGH AI finding → INFO; failure = ensure a SECURITY CRITICAL finding still yields CRITICAL risk in SummaryGenerator. Evidence `.omo/evidence/task-2-pr-triage-srs-build.log`
  Commit: Y | fix(analysis): AI-likelihood is a signal, not a risk severity

- [ ] 3. Fold tier + security flag + suggested action into `SummaryGenerator.computeTier(...)`
  What to do / Must NOT do: No new class. Add `computeTier(List<Finding> findings, PullRequestContext ctx)` to `bot/src/main/java/com/bot/bot/analysis/SummaryGenerator.java` returning a small `TriageResult` record (in `domain`): `tier` (GREEN|YELLOW|RED), `securityFlag` (boolean), `suggestedAction`. Tier from SRS §5: GREEN=coherent+low AI-likelihood+has tests+no security; YELLOW=mixed/complex/off-topic/moderate AI; RED=templated+sweeping-unrelated+no clear intent. `securityFlag`=SEPARATE axis (any SECURITY finding OR SecretsDetectionRule hit) attachable to any tier — must NOT fold security into RED only. `suggestedAction` per SRS §5 table: GREEN→REVIEW_AND_MERGE, YELLOW→MANUAL_CHECK, RED→CONSIDER_CLOSING. Replace the existing `determineRecommendation()` (`:203-215`) with this. `ReviewOrchestrator` calls `computeTier` and threads the `TriageResult` to publisher/labels/email/persistence.
  Parallelization: Wave 1 | Blocked by: T1, T2 | Blocks: T8, T9, T13, T14
  References: bot/src/main/java/com/bot/bot/analysis/SummaryGenerator.java:203-215; bot/src/main/java/com/bot/bot/analysis/heuristics/SecretsDetectionRule.java; docs/SRS.md §5
  Acceptance criteria: unit test `SummaryGeneratorTierTest` covers 3 tier cases + securityFlag-attachable-to-GREEN; `./mvnw -q -pl bot test -Dtest=SummaryGeneratorTierTest` passes.
  QA scenarios: happy = 5 coherent chunks + tests → GREEN/REVIEW_AND_MERGE; failure = secret on GREEN PR → securityFlag true, tier unchanged. Evidence `.omo/evidence/task-3-pr-triage-srs-build.log`
  Commit: Y | feat(analysis): first-class tier + separate security flag, folded into SummaryGenerator (SRS §5)
  // ponytail: no separate SignalAggregator class — tier logic is one method on the generator that already owns the summary.

- [ ] 4. One OpenAI-compatible client × 3 configs + fallback chain (delete OllamaClient/NvidiaNimClient)
  What to do / Must NOT do: `NvidiaNimClient` already uses `/v1/chat/completions`; Ollama also serves that same endpoint. So DELETE `OllamaClient.java` and `NvidiaNimClient.java` and add ONE `bot/src/main/java/com/bot/bot/llm/OpenAiCompatibleClient.java` implementing `LLMClient` (POST `{baseUrl}/v1/chat/completions`, body `{model, messages:[{role:user,content:prompt}], temperature}`, read `choices[0].message.content`, honor timeout + bearer api-key). Add `bot/src/main/java/com/bot/bot/llm/LLMFallbackChain.java` implementing `LLMClient`, holding an ordered `List<OpenAiCompatibleClient>` from `llm.providers` (default order: openai-compatible, nvidia-nim, ollama; Ollama baseUrl must point at `.../v1`). `generateCodeReview` tries each in order, returns first success, logs fallback. Remove the `@ConditionalOnProperty` bean-magic on the old clients. Restructure `LLMProperties` to a `List<ProviderConfig>` (baseUrl, apiKey, model, name) + `providers` order. Inject `LLMFallbackChain` as the single `LLMClient` bean into `LLMReviewEngine`.
  Parallelization: Wave 1 | Blocked by: - | Blocks: T3 (LLMClient bean)
  References: bot/src/main/java/com/bot/bot/llm/LLMClient.java; bot/src/main/java/com/bot/bot/llm/NvidiaNimClient.java:76 (OpenAI format); bot/src/main/java/com/bot/bot/llm/OllamaClient.java:53 (native /api/generate — replaced); bot/src/main/java/com/bot/bot/config/LLMProperties.java:22,25; bot/src/main/java/com/bot/bot/config/WebClientConfig.java
  Acceptance criteria: `./mvnw -q -pl bot test -Dtest=LLMFallbackChainTest` passes (1st down → 2nd used; all down → throws clear error) AND `OllamaClient`/`NvidiaNimClient` no longer compile-referenced.
  QA scenarios: happy = openai returns → NIM/Ollama untouched; failure = openai+NIM down, Ollama(/v1) up → uses Ollama. Evidence `.omo/evidence/task-4-pr-triage-srs-build.log`
  Commit: Y | refactor(llm): collapse 3 clients into 1 OpenAI-compatible × 3 configs (user override)
  // ponytail: all three providers speak /v1/chat/completions — one class, three configs. Ollama reached via its /v1 endpoint. No per-provider subclasses.

- [ ] 5. Add persistence deps + datasource (no Flyway)
  What to do / Must NOT do: In `bot/pom.xml:33-136` add `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` only. In `bot/src/main/resources/application.yaml` add `spring.datasource` (env DATABASE_URL/DATABASE_USER/DATABASE_PASSWORD) and `spring.jpa.hibernate.ddl-auto=update` (ponytail: single stable schema, no migration framework). Do NOT add flyway-core. The `PrAnalysis` @Entity (T6) defines the schema; Hibernate creates/updates the table on boot.
  Parallelization: Wave 1 | Blocked by: - | Blocks: T6, T10
  References: bot/pom.xml; bot/src/main/resources/application.yaml:1-30; docs/SRS.md §9
  Acceptance criteria: `./mvnw -q -pl bot compile` + boot against Testcontainers Postgres creates `pr_analysis` (assert via repository count or PG catalog in test).
  QA scenarios: happy = boot → table exists; failure = missing DATABASE_URL → clear startup error. Evidence `.omo/evidence/task-5-pr-triage-srs-build.log`
  Commit: Y | feat(persistence): JPA + Postgres (ddl-auto=update, no migration dep)
  // ponytail: one small stable table — Hibernate owns the schema, skip the Flyway dependency + migration file.

- [ ] 6. Add `PrAnalysis` entity + repository
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/persistence/PrAnalysis.java` (@Entity, maps V1 columns, @Id @GeneratedValue) and `bot/src/main/java/com/bot/bot/persistence/PrAnalysisRepository.java` (Spring Data) with `findLatest(owner,repo,prNumber)`, `existsByOwnerRepoPrSha(owner,repo,prNumber,commitSha)`, `save(...)`. Do NOT add business logic to the entity.
  Parallelization: Wave 1 | Blocked by: T5 | Blocks: T7, T9
  References: db/migration/V1__pr_analysis.sql; bot/src/main/java/com/bot/bot/domain/PullRequestContext.java
  Acceptance criteria: `./mvnw -q -pl bot test -Dtest=PrAnalysisRepositoryTest` (Testcontainers) save + findLatest returns newest + existsBySha true/false.
  QA scenarios: happy = two analyses same PR different sha → findLatest newest; failure = unknown PR → empty. Evidence `.omo/evidence/task-6-pr-triage-srs-build.log`
  Commit: Y | feat(persistence): PrAnalysis entity + repository

- [ ] 7. Dedupe `synchronize` by commitSha
  What to do / Must NOT do: In `bot/src/main/java/com/bot/bot/service/ReviewOrchestrator.java` `synchronize` (or process entry), before analyzing fetch `prAnalysisRepository.findLatest(owner,repo,prNumber)`; if present and `commitSha == current head` → skip analysis + log "no change since <sha>". Else proceed and store sha (T9). Implements SRS §9 debounce + §10 skip-unchanged. Do NOT skip on title/label change alone (only head commit matters).
  Parallelization: Wave 2 | Blocked by: T1, T5, T6 | Blocks: T9
  References: bot/src/main/java/com/bot/bot/service/ReviewOrchestrator.java; bot/src/main/java/com/bot/bot/github/GitHubApiClient.java (head sha source)
  Acceptance criteria: `ReviewOrchestratorTest` re-sync same sha → second call makes 0 LLM calls (spy) + logs skip; different sha → analyzes.
  QA scenarios: happy = identical sha → cached; failure = force re-run via different sha. Evidence `.omo/evidence/task-7-pr-triage-srs-build.log`
  Commit: Y | feat(orchestrator): dedupe analyze by head commit sha (SRS §9/§10)

- [ ] 8. Apply PR labels from tier
  What to do / Must NOT do: Add `addLabels(owner,repo,prNumber,List<String>,installationId)` to `bot/src/main/java/com/bot/bot/github/GitHubApiClient.java` (PATCH `/repos/{o}/{r}/issues/{n}/labels`). In orchestrator/publisher map tier→labels: RED→[`ai-suspected`,`low-effort`], YELLOW→[`needs-review`], GREEN→[] (configurable). Call after review posted. Do NOT remove existing labels (additive).
  Parallelization: Wave 2 | Blocked by: T3 | Blocks: -
  References: bot/src/main/java/com/bot/bot/github/GitHubApiClient.java; bot/src/main/java/com/bot/bot/engine/ReviewPublisher.java:38-51; docs/SRS.md §7.4
  Acceptance criteria: `GitHubApiClientTest` (MockWebServer) asserts PATCH body contains expected labels for a RED tier.
  QA scenarios: happy = RED → 2 labels; failure = GREEN → no PATCH sent. Evidence `.omo/evidence/task-8-pr-triage-srs-build.log`
  Commit: Y | feat(github): apply tier-based PR labels (SRS §7.4)

- [ ] 9. Persist analysis results
  What to do / Must NOT do: After publish in orchestrator, build + `prAnalysisRepository.save(PrAnalysis)` from tier/securityFlag/summary/findingsJson/commitSha/installationId/status=ANALYZED. Store findings as compact JSON (reuse Finding serializer). Must NOT block the webhook response on DB write failure — log + continue (best-effort persistence, SRS §9).
  Parallelization: Wave 2 | Blocked by: T3, T6, T7 | Blocks: T13
  References: bot/src/main/java/com/bot/bot/service/ReviewOrchestrator.java; bot/src/main/java/com/bot/bot/persistence/PrAnalysis.java
  Acceptance criteria: `ReviewOrchestratorTest` end-to-end → row present in Testcontainers with correct tier + status ANALYZED.
  QA scenarios: happy = analysis saved; failure = DB down → webhook still 200, error logged. Evidence `.omo/evidence/task-9-pr-triage-srs-build.log`
  Commit: Y | feat(persistence): store analysis + action history

- [ ] 10. Add Spring Mail (pluggable SMTP) config + `JavaMailSender`
  What to do / Must NOT do: In `bot/pom.xml` add `spring-boot-starter-mail`. New `bot/src/main/java/com/bot/bot/config/MailProperties.java` (host, port, username, password, from, List<String> maintainerEmails, boolean enabled) bound to `app.mail.*` and env. New `bot/src/main/java/com/bot/bot/config/MailConfig.java` exposing `JavaMailSender` only when enabled; if disabled, bean is a no-op that logs. Do NOT hardcode a provider.
  Parallelization: Wave 2 | Blocked by: T5 | Blocks: T12, T13
  References: bot/pom.xml; bot/src/main/java/com/bot/bot/config/AppProperties.java; bot/.env.example
  Acceptance criteria: `./mvnw -q -pl bot compile`; `MailConfigTest` asserts sender present when enabled, no-op when disabled.
  QA scenarios: happy = enabled + Mailtrap creds → sender built; failure = disabled → email call no-ops. Evidence `.omo/evidence/task-10-pr-triage-srs-build.log`
  Commit: Y | feat(mail): pluggable SMTP via Spring Mail (provider-agnostic)

- [ ] 11. Digest email scheduler (configurable cron)
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/email/DigestService.java` with `@Scheduled`/TriggerTask using `app.mail.digest-cron` (default daily 18:00). Query `PrAnalysis` where `status=ANALYZED` and `createdAt > lastDigestAt` (track `lastDigestAt` in a small `Meta` table/row). Build one email grouping new/updated PRs. Call only when `mail.enabled`. Do NOT send if zero PRs.
  Parallelization: Wave 3 | Blocked by: T9, T10 | Blocks: -
  References: bot/src/main/java/com/bot/bot/persistence/PrAnalysisRepository.java; bot/src/main/java/com/bot/bot/config/MailProperties.java
  Acceptance criteria: `DigestServiceTest` (fixed clock) → email built with 3 PRs, `lastDigestAt` advanced; no email when empty.
  QA scenarios: happy = 3 new PRs → 1 email, 3 rows; failure = none new → no send. Evidence `.omo/evidence/task-11-pr-triage-srs-build.log`
  Commit: Y | feat(email): daily digest scheduler (SRS §7.1)

- [ ] 12. Threshold immediate email alert
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/email/ThresholdAlertService.java`: when a fresh analysis tier==RED (or securityFlag) and not already alerted, send an immediate email (respect `app.mail.threshold-tier`). Reuse `JavaMailSender`. Track alerted via `PrAnalysis.status`/`alerted` flag to avoid resends. Do NOT spam on every sync (dedupe via T7 already limits resync).
  Parallelization: Wave 3 | Blocked by: T3, T10 | Blocks: T14
  References: bot/src/main/java/com/bot/bot/analysis/SignalAggregator.java; bot/src/main/java/com/bot/bot/config/MailProperties.java; docs/SRS.md §7.2
  Acceptance criteria: `ThresholdAlertServiceTest` → RED analysis → 1 immediate email; GREEN → none; same PR re-alert suppressed.
  QA scenarios: happy = RED → alert; failure = already alerted → no duplicate. Evidence `.omo/evidence/task-12-pr-triage-srs-build.log`
  Commit: Y | feat(email): immediate alert for RED/security PRs (SRS §7.2)

- [ ] 13. Digest/alert email template (scannable + action links)
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/email/EmailTemplate.java` rendering an HTML table: owner/repo#n, title, tier emoji, 1-line summary, link to full analysis, per-row action buttons (approve / request-changes / close) built from `TokenService` (T16) URLs. Keep it plain/readable; no heavy CSS framework. Both DigestService + ThresholdAlertService use it.
  Parallelization: Wave 3 | Blocked by: T11, T16 | Blocks: T19
  References: docs/SRS.md §7.1/§7.3; bot/src/main/java/com/bot/bot/persistence/PrAnalysis.java
  Acceptance criteria: `EmailTemplateTest` asserts row per PR + 3 action URLs with `?token=` for a mock analysis.
  QA scenarios: happy = 2 PRs → 2 rows × 3 links; failure = missing summary → placeholder text. Evidence `.omo/evidence/task-13-pr-triage-srs-build.log`
  Commit: Y | feat(email): scannable digest template with action links

- [ ] 14. Signed, expiring, single-use action token service
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/actions/TokenService.java`: `generate(owner,repo,prNumber,action)` → HMAC-SHA256 over `{owner|repo|prNumber|action|expiryEpoch}` signed with `app.action-secret`, URL-safe, expiry default 7d, single-use (store used-token hash or mark `PrAnalysis.actionTaken`). `verify(token)` → returns payload or throws (bad sig / expired / used). Build URL `{app.base-url}/action?token=...&do=approve|request-changes|close`. Do NOT put the secret in logs; rotate-friendly (secret from env).
  Parallelization: Wave 4 | Blocked by: T3, T9 | Blocks: T13, T16, T22
  References: docs/SRS.md §7.3/§8/§9; bot/src/main/java/com/bot/bot/persistence/PrAnalysis.java
  Acceptance criteria: `TokenServiceTest` → round-trip generate/verify; tampered token rejected; expired rejected; reused token rejected after first verify.
  QA scenarios: happy = valid token → payload; failure = clock past expiry → rejected. Evidence `.omo/evidence/task-14-pr-triage-srs-build.log`
  Commit: Y | feat(actions): signed expiring single-use action tokens (SRS §9)

- [ ] 15. Add GitHub close + request-changes operations
  What to do / Must NOT do: In `bot/src/main/java/com/bot/bot/github/GitHubApiClient.java` add `closePullRequest(owner,repo,prNumber,installationId)` (PATCH `/repos/{o}/{r}/pulls/{n}` state=closed) and ensure `submitReview` accepts event `REQUEST_CHANGES` (it already takes `event`). Auth via installation token. Do NOT auto-merge.
  Parallelization: Wave 4 | Blocked by: T14, T17 | Blocks: T18
  References: bot/src/main/java/com/bot/bot/github/GitHubApiClient.java (submitReview); bot/src/main/java/com/bot/bot/github/GitHubJwtGenerator.java
  Acceptance criteria: `GitHubApiClientTest` (MockWebServer) asserts PATCH pulls close body + review event REQUEST_CHANGES.
  QA scenarios: happy = close → 200 state closed; failure = invalid install token → clear error. Evidence `.omo/evidence/task-15-pr-triage-srs-build.log`
  Commit: Y | feat(github): close PR + request-changes review event

- [ ] 16. Action Handler controller (approve / request-changes / close)
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/web/ActionController.java` GET `/action?token=&do=`. Verify via `TokenService`; map do→GitHub call (approve→submitReview APPROVE; request-changes→submitReview REQUEST_CHANGES; close→closePullRequest); on success mark `PrAnalysis.actionTaken` + status ACTIONED; render a minimal HTML result page (success / error). Must NOT allow any action without valid token; must NOT auto-merge.
  Parallelization: Wave 4 | Blocked by: T14 | Blocks: T13, T22
  References: docs/SRS.md §8/§9; bot/src/main/java/com/bot/bot/actions/TokenService.java; bot/src/main/java/com/bot/bot/web/GitHubWebhookController.java (existing controller style)
  Acceptance criteria: `ActionControllerTest` (MockMvc) valid token+do=close → 200 + GitHub close called + status ACTIONED; bad token → 400; used token → 410.
  QA scenarios: happy = click link → action done; failure = tampered token → no action. Evidence `.omo/evidence/task-16-pr-triage-srs-build.log`
  Commit: Y | feat(actions): one-click action handler (SRS §8)

- [ ] 17. Wire action links into email + enable in orchestrator
  What to do / Must NOT do: In `DigestService`/`ThresholdAlertService` use `EmailTemplate` (T13) which calls `TokenService` (T16). Ensure `app.base-url` + `app.action-secret` present (fail-fast config check at startup if email/actions enabled but secret missing). No logic change beyond wiring.
  Parallelization: Wave 4 | Blocked by: T14, T15 | Blocks: T18
  References: bot/src/main/java/com/bot/bot/email/DigestService.java; bot/src/main/java/com/bot/bot/email/ThresholdAlertService.java
  Acceptance criteria: integration test → digest email contains working `/action?token=` URLs for a seeded PR.
  QA scenarios: happy = email link verifiable; failure = missing base-url → startup warns, links omitted. Evidence `.omo/evidence/task-17-pr-triage-srs-build.log`
  Commit: Y | feat(email): embed one-click action links

- [ ] 18. End-to-end action flow test
  What to do / Must NOT do: New `bot/src/test/java/com/bot/bot/actions/ActionFlowIntegrationTest.java`: seed PrAnalysis, generate token, call `/action?do=close` via MockMvc, assert GitHub MockWebServer received close + PrAnalysis.status=ACTIONED. Covers approve + request-changes too.
  Parallelization: Wave 4 | Blocked by: T15, T16, T17 | Blocks: T19
  References: bot/src/test/java/com/bot/bot/web/; bot/src/main/java/com/bot/bot/persistence/PrAnalysisRepository.java
  Acceptance criteria: `./mvnw -q -pl bot test -Dtest=ActionFlowIntegrationTest` passes all 3 actions.
  QA scenarios: happy = 3 actions each update status; failure = double-use → second 410. Evidence `.omo/evidence/task-18-pr-triage-srs-build.log`
  Commit: Y | test(actions): end-to-end action flow

- [ ] 19. Repo-context + linked-issue fetch for LLM
  What to do / Must NOT do: Extend `bot/src/main/java/com/bot/bot/github/GitHubApiClient.java` with `fetchFile(owner,repo,path,installationId)` (GET contents, README/CONTRIBUTING) + `fetchLinkedIssues(owner,repo,prNumber,installationId)` (parse `pulls/{n}` body `closing_issues` / timeline). In orchestrator, gather repo-context + linked-issue text and prepend to the LLM prompt (SRS §4). Must NOT fetch large binaries; guard file size.
  Parallelization: Wave 3 | Blocked by: T13, T14, T18 | Blocks: -
  References: bot/src/main/java/com/bot/bot/github/GitHubApiClient.java:147 (fetchDiff); docs/SRS.md §4
  Acceptance criteria: `GitHubApiClientTest` asserts README + linked issue fetched and prompt includes their text (spy LLMClient).
  QA scenarios: happy = PR closing #12 → issue body in prompt; failure = missing README → prompt still built. Evidence `.omo/evidence/task-19-pr-triage-srs-build.log`
  Commit: Y | feat(github): repo-context + linked-issue LLM enrichment (SRS §4)

- [ ] 20. Per-installation maintainer config
  What to do / Must NOT do: New `bot/src/main/java/com/bot/bot/persistence/MaintainerConfig.java` (@Entity: installationId PK, maintainerEmails json, digestCron, thresholdTier, labelsEnabled, emailEnabled, actionsEnabled) + repository. Seed from `app.*` defaults when absent. New `bot/src/main/java/com/bot/bot/config/ConfigService.java` resolving config by installationId with global fallback.
  Parallelization: Wave 5 | Blocked by: T9, T11, T12 | Blocks: T21
  References: bot/src/main/java/com/bot/bot/config/MailProperties.java; docs/SRS.md §10/§11
  Acceptance criteria: `ConfigServiceTest` → per-installation overrides global default; missing row falls back.
  QA scenarios: happy = custom cron per install; failure = unknown install → defaults. Evidence `.omo/evidence/task-20-pr-triage-srs-build.log`
  Commit: Y | feat(config): per-installation maintainer settings

- [ ] 21. Wire maintainer config into scheduler/labels/thresholds
  What to do / Must NOT do: Refactor `DigestService`/`ThresholdAlertService`/`label logic` to read `ConfigService` by installationId (digestCron, thresholdTier, toggles). Scheduler must support per-installation cron (use `TaskScheduler` + dynamic `Trigger` per config, not a single `@Scheduled`). Do NOT break the global-default path.
  Parallelization: Wave 5 | Blocked by: T20 | Blocks: -
  References: bot/src/main/java/com/bot/bot/email/DigestService.java; bot/src/main/java/com/bot/bot/config/ConfigService.java
  Acceptance criteria: test → two installs with different cron both scheduled; disabled email install sends nothing.
  QA scenarios: happy = install A daily, B hourly; failure = emailEnabled=false → no send. Evidence `.omo/evidence/task-21-pr-triage-srs-build.log`
  Commit: Y | feat(config): per-installation cadence + toggles

- [ ] 22. Read-only dashboard (Thymeleaf)
  What to do / Must NOT do: In `bot/pom.xml` add `spring-boot-starter-thymeleaf`. New `bot/src/main/java/com/bot/bot/web/DashboardController.java` GET `/` listing recent `PrAnalysis` (tier emoji, summary snippet, action buttons via `TokenService`). Read-only; reuse analysis data + action links. Minimal inline CSS. Must NOT expose secrets; must NOT allow writes via GET.
  Parallelization: Wave 6 | Blocked by: T6, T14, T16 | Blocks: -
  References: bot/src/main/java/com/bot/bot/persistence/PrAnalysisRepository.java; docs/SRS.md §11
  Acceptance criteria: `DashboardControllerTest` (MockMvc) GET `/` → 200, lists seeded PRs with action URLs; no POST endpoints.
  QA scenarios: happy = 5 PRs shown; failure = empty DB → friendly empty state. Evidence `.omo/evidence/task-22-pr-triage-srs-build.log` + screenshot `.omo/evidence/task-22-pr-triage-srs-build.png`
  Commit: Y | feat(ui): read-only triage dashboard (SRS §11)

## Final verification wave
> Runs in parallel after ALL todos. ALL must APPROVE. Surface results and wait for the user's explicit okay before declaring complete.
- [ ] F1. Plan compliance audit — every Must-have present, every Must-NOT-have absent, references match code.
- [ ] F2. Code quality review — ponytail check (no unrequested abstraction, shortest diff), no dead config left, compile clean.
- [ ] F3. Real manual QA — start app against Testcontainers Postgres + Mailtrap, open a PR via GitHub sandbox/webhook, confirm: tier label, digest email with working action links, click close → PR closed + dashboard shows ACTIONED.
- [ ] F4. Scope fidelity — confirm no auto-merge/reject, no Claude, no rewrite, no billing UI.

## Commit strategy
- **Repo:** `git init -b main`; default branch is `main`. The project is not yet a git repo.
- **Branches:** every change is made on a feature branch (`fix/...`, `feat/...`, `refactor/...`). Nothing is committed directly to `main` during development.
- **Merge to main:** after a wave's work is verified (tests green, evidence captured), merge/squash the feature branch into `main`. Only then does `main` advance.
- **Commits:** one commit per todo (see each todo's `Commit:` line). Conventional commits, scope = module. NO `Co-Authored-By` trailer in any message (user rule).
- **No remote push** unless the user explicitly asks.
- **PR:** opened only after F1–F4 approve and the user gives an explicit go.

## Session workflow (compaction)
- When the context window reaches ~70%, stop and produce a compact handoff (`/handoff` or equivalent) capturing Objective / Important Details / Work State / Next Move / Relevant Files, then resume. Do NOT let the session decay mid-build.
- After compaction, continue from the gate above — never re-run exploration that the draft/plan already records.

## Success criteria
- `./mvnw -q -pl bot test` green (incl. Testcontainers Postgres + MockMvc).
- A PR opened against a test repo yields: GitHub review + tier label + persisted analysis + (if RED) instant alert + daily digest with working one-click action links + dashboard reflects actions.
- `synchronize` with unchanged head sha produces zero re-analysis.
- No Claude, no auto-merge, no rewrite, no billing — scope honored.
