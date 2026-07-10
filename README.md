# PR Triage Bot (`glint`)

A webhook-driven GitHub App that automatically triages every incoming pull request:
it fetches the diff, runs cheap heuristics **and** an LLM review in parallel,
merges and ranks the findings, computes a triage tier, and posts a structured
review comment (plus optional labels, email digest, and one-click actions) so a
maintainer can triage a flood of PRs in minutes instead of hours.

> The human stays in the loop. The bot never merges or auto-closes — it leaves
> an `APPROVE`/comment and optional one-click buttons. Closing a PR is always a
> deliberate signed-action click.

- **Stack:** Spring Boot 4.0.2 (Java 21), Maven, PostgreSQL, WebFlux `WebClient`, Thymeleaf dashboard.
- **App name:** `glint` (see `spring.application.name` in `bot/src/main/resources/application.yaml`).
- **Design intent:** see [`docs/SRS.md`](docs/SRS.md) (Solution Requirements Spec).

## Features

- Verifies GitHub webhook signatures (HMAC `X-Hub-Signature-256`) on every request.
- Reacts to `pull_request` events: `opened`, `synchronize`, `reopened`.
- Dual signal engine:
  - **Heuristics** (`com.bot.bot.analysis.heuristics.*`) — secrets, commit-message style, diff shape, account age, comment/code ratio, boilerplate phrases. Runs synchronously, in parallel.
  - **LLM review** (`com.bot.bot.analysis.LLMReviewEngine`) — per-diff-chunk review via an OpenAI-compatible endpoint, with an ordered provider fallback chain.
- Per-installation **triage tier**: 🟢 GREEN / 🟡 YELLOW / 🔴 RED, plus a separate `security` flag.
- Posts a markdown review with a severity breakdown and optional inline comments.
- Applies `triage:green|yellow|red` (+ `security`) labels.
- **Email**: daily digest grouped per installation + immediate alert for RED/security PRs.
- **One-click actions** (`/action?token=…&do=…`): approve / request-changes / close, via signed single-use tokens — no auto-merge.
- **Dashboard** (`/`) listing recent PRs with action links.
- Dedupe: skips re-analysis when the PR's commit SHA is unchanged.
- Graceful shutdown, Prometheus metrics, custom health indicators.

## Quick start

1. [Set up the GitHub App and run the bot](docs/tutorial-getting-started.md) — end-to-end walkthrough.
2. [Configure](docs/howto-setup.md) providers, mail, and per-installation options.
3. [Reference: configuration](docs/reference-configuration.md) and [Reference: API & endpoints](docs/reference-api.md).
4. [How the analysis pipeline works](docs/explanation-analysis-pipeline.md).

## Repository layout

```
pr-triage/
├── bot/                      # Spring Boot application (the bot)
│   ├── src/main/java/com/bot/bot/
│   │   ├── BotApplication.java        # entry point, .env loader
│   │   ├── webhook/                   # GitHubWebhookController, WebhookSignatureVerifier
│   │   ├── service/                   # ReviewOrchestrator (pipeline)
│   │   ├── analysis/                  # heuristics + LLMReviewEngine + SummaryGenerator
│   │   ├── llm/                       # LLMFallbackChain, OpenAiCompatibleClient
│   │   ├── github/                    # GitHubApiClient, GitHubJwtGenerator
│   │   ├── engine/                    # FindingMerger, ReviewPublisher
│   │   ├── actions/                   # TokenService (signed action tokens)
│   │   ├── web/                       # DashboardController, ActionController
│   │   ├── email/                     # MailService, DigestService, ThresholdAlertService
│   │   ├── config/                    # *Properties, ConfigService, ActionsConfig
│   │   ├── persistence/               # PrAnalysis, MaintainerConfig, repositories
│   │   └── domain/                    # Finding, ChangeChunk, PullRequestContext, TriageResult
│   ├── src/main/resources/application.yaml
│   ├── .env.example
│   └── pom.xml
└── docs/                     # standalone documentation (this set)
    ├── SRS.md                       # design spec
    ├── tutorial-getting-started.md
    ├── howto-setup.md
    ├── reference-configuration.md
    ├── reference-api.md
    └── explanation-analysis-pipeline.md
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the component map and data flow.

## Build & run (summary)

```bash
cd bot
cp .env.example .env        # fill in GITHUB_* and LLM_* values
# provide certs/github-app.pem (GitHub App private key)
mvn -q spring-boot:run      # serves on :8080
```

See the [tutorial](docs/tutorial-getting-started.md) for the full steps,
including creating the GitHub App and pointing its webhook at `/webhook/github`.

## License

See repository license files.
