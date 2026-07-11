# How-to: Setup

Practical recipes for configuring `glint` after the [getting-started tutorial](tutorial-getting-started.md).
For the full key reference, see [Reference: configuration](reference-configuration.md).

## GitHub App

`glint` authenticates as a GitHub App (not a user token). It generates a JWT
from the private key, exchanges it for a short-lived installation token
(cached ~55 min), and calls the REST API.

Required env (see `.env.example`):

| Key | Purpose |
|---|---|
| `GITHUB_APP_ID` | The numeric App ID. |
| `GITHUB_CLIENT_ID` | OAuth client id (for completeness). |
| `GITHUB_WEBHOOK_SECRET` | Shared secret used to verify `X-Hub-Signature-256`. |
| `GITHUB_PRIVATE_KEY_PATH` | Filesystem path to the App's `.pem` private key. |

The App must be subscribed to the **Pull request** event and have
**Pull requests: read & write**, **Contents: read-only**, **Metadata: read-only**
permissions (set in the GitHub App console — see the tutorial).

## LLM providers

`glint` reviews diff chunks with an LLM. Providers are a **list**; the
`LLMFallbackChain` tries them in order until one succeeds.

Configure in `bot/src/main/resources/application.yaml` under `llm.providers`:

```yaml
llm:
  enabled: ${LLM_ENABLED:true}
  timeout-seconds: ${LLM_TIMEOUT_SECONDS:60}
  providers:
    - provider-type: openai-compatible   # openai-compatible | nvidia-nim | ollama
      name: primary
      base-url: https://api.openai.com/v1   # or set LLM_BASE_URL (overrides)
      model: gpt-4o-mini                    # or set LLM_MODEL (overrides)
      api-key: ${LLM_API_KEY}               # or set LLM_API_KEY (overrides)
    - provider-type: nvidia-nim
      name: fallback
      base-url: http://localhost:8000
      model: meta/llama-3.1-8b-instruct
      api-key: ${LLM_API_KEY}
```

- **ENV overrides**: `LLM_PROVIDER_TYPE`, `LLM_MODEL`, and `LLM_BASE_URL` override
  the *first* provider's `provider-type`/`model`/`base-url`; `LLM_API_KEY`
  seeds its `api-key`. `LLM_ENABLED` toggles the whole review path and
  `LLM_TIMEOUT_SECONDS` sets the per-request timeout.
- **All providers share** one `OpenAiCompatibleClient` implementation (the
  OpenAI `/chat/completions` request/response shape).
- On a provider failure the chain moves to the next; if all fail, the review
  proceeds **heuristics-only** (no LLM findings). See
  [analysis pipeline](explanation-analysis-pipeline.md#llm-fallback).

Toggle LLM entirely with `app.llm-enabled: false` (env `LLM_ENABLED`).

## Email

Email is optional. When enabled, `glint` sends:

- a **daily digest** of new PRs per installation (cron, default `0 0 18 * * *`),
- an **immediate alert** for RED-tier or security-flagged PRs.

| Key | Purpose |
|---|---|
| `MAIL_ENABLED` | Master switch. |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP credentials. |
| `MAIL_FROM` | From address. |
| `MAIL_DIGEST_CRON` | Digest schedule (Spring cron). |
| `MAIL_MAINTAINERS` | Comma-separated default recipient list. |

## One-click actions

`glint` posts `Approve / Request changes / Close` buttons on each PR. These hit
`/action?token=…&do=…` where the token is an HMAC-signed, single-use value
(`TokenService`). The bot **never auto-merges**; `close` sets `state=closed`
only.

To enable actions you must set `app.action-secret` (env `APP_ACTION_SECRET`).
Without it, `actionsEnabled` is false and no buttons are rendered. `app.base-url`
should be the public base URL so the dashboard can build correct action links.

## Per-installation configuration

Global defaults in `application.yaml`/env can be **overridden per GitHub
installation** via the `maintainer_config` table (one row per
`installation_id`). `ConfigService.resolve(installationId)` merges the two.

| Setting | Global default | Overridable? |
|---|---|---|
| `digestCron` | from `mail.digest-cron` | ✅ |
| `thresholdTier` | `RED` (alert when tier ≥ this) | ✅ |
| `labelsEnabled` | `true` | ✅ |
| `emailEnabled` | `mail.enabled` | ✅ |
| `actionsEnabled` | `action-secret` present | ✅ |
| `maintainerEmails` | `mail.maintainers` | ✅ |

Override by inserting/updating a `maintainer_config` row for the installation id
with the desired non-null fields. `ResolvedConfig` reports the effective values
(`fromDefaults` vs `overridden`).

## Manual triage (human fallback)

If the bot is unavailable, maintainers can still triage manually. The dashboard
(`/`) is read-only and always shows the latest analyzed PRs with action links,
so a human can click through without the bot re-running.
