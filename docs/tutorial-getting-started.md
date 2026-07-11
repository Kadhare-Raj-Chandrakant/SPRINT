# Tutorial: Getting Started

This tutorial takes you from zero to a working `glint` PR-triage bot that
triages pull requests on a real GitHub repo. It assumes Java 21, Maven, and a
PostgreSQL database are available.

> See also: [How-to: setup](howto-setup.md), [Reference: configuration](reference-configuration.md),
> [Reference: API](reference-api.md). The design rationale lives in [SRS.md](SRS.md)
> and [ARCHITECTURE.md](../ARCHITECTURE.md).

## 1. Create a GitHub App

1. Go to **GitHub → Settings → Developer settings → GitHub Apps → New GitHub App**
   (or your org's app settings).
2. Set:
   - **Homepage URL**: `http://localhost:8080` (or your deployment URL).
   - **Webhook**: enable. **Webhook URL**: `https://<your-host>/webhook/github`.
     **Webhook secret**: generate a long random string — you'll put it in
     `GITHUB_WEBHOOK_SECRET`.
   - **Permissions**:
     - Pull requests: **Read & write** (to post reviews/comments/labels).
     - Contents: **Read-only** (to fetch diffs).
     - Metadata: **Read-only**.
   - **Subscribe to events**: ☑ **Pull request**.
3. Create the app. Note the **App ID** and **Client ID**.
4. Generate and download a **private key** (`.pem`). Save it as
   `bot/certs/github-app.pem`.
5. Install the app on the repo(s) you want triaged. Note the **Installation ID**
   (it appears in the install URL and in webhook payloads).

## 2. Prepare the environment

```bash
cd pr-triage/bot
cp .env.example .env
```

Fill in at least:

```dotenv
# GitHub App
GITHUB_APP_ID=123456
GITHUB_CLIENT_ID=Iv1.xxxxx
GITHUB_WEBHOOK_SECRET=********************************
GITHUB_PRIVATE_KEY_PATH=certs/github-app.pem
GITHUB_API_URL=https://api.github.com

# LLM (OpenAI-compatible). ENV seeds the first provider in application.yaml.
LLM_PROVIDER_TYPE=openai-compatible
LLM_MODEL=gpt-4o-mini
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-...
LLM_ENABLED=true
LLM_TIMEOUT_SECONDS=60

# App (one-click actions)
APP_ACTION_SECRET=random-32-byte-secret-for-hmac
APP_BASE_URL=http://localhost:8080

# PostgreSQL
DATABASE_URL=jdbc:postgresql://localhost:5432/pr_triage
DATABASE_USER=postgres
DATABASE_PASSWORD=************

# Mail (optional — skip to disable email)
MAIL_ENABLED=false
```

> The full, annotated list of every variable (with defaults) lives in
> `bot/.env.example`. That file is the source of truth for configuration.

Place the private key:

```bash
mkdir -p bot/certs
mv ~/Downloads/your-app.pem bot/certs/github-app.pem
```

## 3. Build and run

```bash
cd pr-triage/bot
mvn -q spring-boot:run
```

The service starts on port `8080`. Confirm liveness:

```bash
curl -i http://localhost:8080/webhook/health
# HTTP/1.1 200 OK
# OK
```

## 4. Expose the webhook (for local testing)

GitHub must reach your `/webhook/github` endpoint. Use a tunnel (e.g. ngrok):

```bash
ngrok http 8080
# set the GitHub App webhook URL to https://<id>.ngrok.io/webhook/github
```

## 5. Trigger a triage

Open a PR on an installed repo (or push to an open one to fire `synchronize`).
Within a few seconds the bot will:

- post a review comment with a 🟢/🟡/🔴 tier and a finding breakdown,
- apply `triage:green|yellow|red` (+ `security`) labels,
- appear in the dashboard at `http://localhost:8080/`.

Open the dashboard to see the PR and the one-click **Approve / Request changes /
Close** buttons.

## 6. Verify it worked

- **Review posted?** Check the PR's "Files changed" / Conversation tab.
- **Labels?** Look for `triage:*` labels.
- **Dashboard?** `GET /` lists recent PRs with action links.
- **Logs:** every log line carries a `traceId` equal to the webhook's
  `X-GitHub-Delivery` header — grep it to follow one PR through async threads.

## 7. Next steps

- [Configure LLM providers and fallback](howto-setup.md#llm-providers).
- [Enable email digests/alerts](howto-setup.md#email).
- [Tune per-installation behavior](howto-setup.md#per-installation-configuration).
- [Understand the tier logic](explanation-analysis-pipeline.md).

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `401` from GitHub on token exchange | Wrong App ID / private key path | Re-check `GITHUB_APP_ID`, `GITHUB_PRIVATE_KEY_PATH`. |
| Webhook returns `401` / `403` | Bad HMAC secret | Ensure `GITHUB_WEBHOOK_SECRET` matches the GitHub App setting. |
| No comment posted | `pull_request` not subscribed, or action not in `{opened,synchronize,reopened}` | Re-check App event subscription. |
| `synchronize` re-triggers repeatedly | Force-push loop | Bot dedupes by commit SHA; wait for the SHA to settle. |
| LLM findings missing, heuristics only | Provider down / bad key | Check logs; bot continues with heuristics. See [fallback](explanation-analysis-pipeline.md#llm-fallback). |
