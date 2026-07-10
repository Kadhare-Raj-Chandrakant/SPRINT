# Reference: Configuration

Every configuration key `glint` reads, grouped by source. Env vars take
precedence over `application.yaml`. See [how-to: setup](howto-setup.md) for
recipes.

## `application.yaml` — `app.*` (AppProperties)

| Key | Type | Default | Env override | Notes |
|---|---|---|---|---|
| `app.port` | int | `8080` | `PORT` | HTTP listen port. |
| `app.heuristics-enabled` | bool | `true` | `HEURISTICS_ENABLED` | Run heuristic rules. |
| `app.llm-enabled` | bool | `true` | `LLM_ENABLED` | Run LLM review. |
| `app.auto-approve` | bool | `false` | `AUTO_APPROVE` | `APPROVE` review only when **no findings**. |
| `app.inline-comments` | bool | `true` | `INLINE_COMMENTS` | Post inline per-finding comments. |
| `app.review-summary-enabled` | bool | `true` | `REVIEW_SUMMARY_ENABLED` | Post the markdown summary review. |
| `app.action-secret` | string | — | `ACTION_SECRET` | Enables one-click actions when set. |
| `app.base-url` | string | — | `BASE_URL` | Public base URL for dashboard action links. |

## `application.yaml` — `github.*` (GitHubProperties)

| Key | Type | Env override | Notes |
|---|---|---|---|
| `github.app-id` | string | `GITHUB_APP_ID` | Numeric App ID. |
| `github.client-id` | string | `GITHUB_CLIENT_ID` | OAuth client id. |
| `github.webhook-secret` | string | `GITHUB_WEBHOOK_SECRET` | HMAC verification secret. |
| `github.private-key-path` | string | `GITHUB_PRIVATE_KEY_PATH` | Path to `.pem`. |

## `application.yaml` — `llm.*` (LLMProperties + ProviderConfig)

```yaml
llm:
  providers:
    - provider-type: openai   # only "openai" supported (OpenAI-compatible shape)
      name: primary
      base-url: https://api.openai.com/v1
      model: gpt-4o-mini
      api-key: ${LLM_API_KEY}
```

| Key | Type | Env override | Notes |
|---|---|---|---|
| `llm.providers[].provider-type` | string | — | Must be `openai`. |
| `llm.providers[].name` | string | — | Logical name (used in logs). |
| `llm.providers[].base-url` | string | `LLM_BASE_URL` (1st provider) | Endpoint base. |
| `llm.providers[].model` | string | `LLM_MODEL` (1st provider) | Model id. |
| `llm.providers[].api-key` | string | `${LLM_API_KEY}` etc. | Resolved from env. |

The `LLMFallbackChain` iterates `llm.providers` in list order.

## `application.yaml` — `mail.*` (MailProperties)

| Key | Type | Default | Env override | Notes |
|---|---|---|---|---|
| `mail.enabled` | bool | `false` | `MAIL_ENABLED` | Master email switch. |
| `mail.host` | string | — | `MAIL_HOST` | SMTP host. |
| `mail.port` | int | — | `MAIL_PORT` | SMTP port. |
| `mail.username` | string | — | `MAIL_USERNAME` | SMTP user. |
| `mail.password` | string | — | `MAIL_PASSWORD` | SMTP password. |
| `mail.from` | string | — | `MAIL_FROM` | From address. |
| `mail.digest-cron` | string | `0 0 18 * * *` | `MAIL_DIGEST_CRON` | Digest schedule. |
| `mail.maintainers` | list | — | `MAIL_MAINTAINERS` | Default recipients (comma-sep). |

## `application.yaml` — `spring.datasource` (PostgreSQL)

| Key | Env override | Notes |
|---|---|---|
| `spring.datasource.url` | `DATABASE_URL` | JDBC URL. |
| `spring.datasource.username` | `DATABASE_USER` | DB user. |
| `spring.datasource.password` | `DATABASE_PASSWORD` | DB password. |

`spring.jpa.hibernate.ddl-auto: update` — schema auto-managed.

## `application.yaml` — server / shutdown

| Key | Value | Notes |
|---|---|---|
| `server.port` | `${app.port:8080}` | HTTP port. |
| `server.shutdown` | `graceful` | Let in-flight reviews finish. |
| `spring.lifecycle.timeout-per-shutdown-phase` | `30s` | Max wait on shutdown. |
| `server.tomcat.max-http-form-post-size` / `max-swallow-size` | `5MB` | Webhook payload limit. |

## Runtime constants (not configurable)

| Constant | Value | Where |
|---|---|---|
| Max ingest file bytes | `800_000` | diff chunking |
| Min / max chunk size | `200` / `9000` chars | `UnifiedDiffParser` |
| Max chunks per PR | `60` | diff chunking |
| Installation token cache | `~55 min` | `GitHubApiClient` |
| Action token TTL | `15 min` (single-use) | `TokenService` |
| Threshold tier default | `RED` | `ConfigService` |

## Per-installation overrides

`mail.maintainers`, digest cron, threshold tier, labels/email/actions toggles,
and recipient list can be overridden per `installation_id` via the
`maintainer_config` table. See [how-to: setup → per-installation](howto-setup.md#per-installation-configuration).
