# Troubleshooting: Setup & Operational Issues

Real issues hit during first deployment. If something doesn't work, check here
before diving into the source.

> **Audience:** Anyone setting up `glint` for the first time, especially behind
> ngrok or with Gmail.
> **Prerequisites:** [tutorial-getting-started.md](tutorial-getting-started.md),
> [howto-setup.md](howto-setup.md).

---

## 1. Webhook returns 401 (`X-Hub-Signature-256` mismatch)

### Symptom

GitHub shows failed webhook deliveries with "HTTP 401: Signature does not match".

### Root cause (ngrok)

If you expose the bot through **ngrok**, ngrok re-encodes the request body
(especially non-UTF-8 bytes in JSON). Spring Boot's `@RequestBody String`
deserializes the body **once** via its message converters, but the signature
verification reads from the **same** input stream — which is already consumed.

This applies *regardless* of whether you set the correct `GITHUB_WEBHOOK_SECRET`.

### Fix

Change the controller to read the raw body bytes from `HttpServletRequest`
before any Spring converters touch it:

```java
@PostMapping("/github")
public ResponseEntity<String> handleGitHubWebhook(
        HttpServletRequest request,                    // ← not @RequestBody
        @RequestHeader("X-Hub-Signature-256") String signature,
        @RequestHeader("X-GitHub-Event") String eventType,
        @RequestHeader("X-GitHub-Delivery") String deliveryId) {

    byte[] rawBytes = request.getInputStream().readAllBytes();
    String payload = new String(rawBytes, StandardCharsets.UTF_8);

    // Now pass rawBytes to verifySignature() instead of payload.getBytes()
    if (!verifier.verifySignature(rawBytes, signature)) {
        return ResponseEntity.status(401).body("Unauthorized");
    }
    // ...
}
```

See commit `9e02437` for the full change (includes test updates).

---

## 2. Email not sent / SMTP connection fails

### Symptom

No alert emails arrive. Logs show:
```
jakarta.mail.MessagingException: Could not convert socket to TLS
```

### Root cause

**Gmail SMTP (`smtp.gmail.com:587`)** requires **STARTTLS** — the connection
starts as plaintext and upgrades to TLS. Spring Mail does not enable this by
default.

### Fix

Add the property in `MailConfig.java`:

```java
javaMail.put("mail.smtp.starttls.enable", "true");
```

If you use port **465** (SMTPS) instead, TLS is implicit and STARTTLS is not
needed, but you'll also need:

```java
javaMail.put("mail.smtp.socketFactory.port", "465");
```

### Environment variables

```dotenv
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=your-app-password         # Gmail App Password, NOT your account password
MAIL_FROM=you@gmail.com
MAIL_MAINTAINERS=you@gmail.com
```

See commit `85acac2`.

---

## 3. Action link returns 500 / `Unable to access lob stream`

### Symptom

Clicking an approve/reject/close link from the email results in a white error
page. Bot logs show:
```
org.hibernate.JpaSystemException: Unable to access lob stream
    at com.bot.bot.web.ActionController.handleAction:52
```

### Root cause

Hibernate 7.x uses **lazy LOB streams** for fields annotated with `@Lob`.
When the repository method returns the entity and the transaction commits, the
LOB stream is closed. Any subsequent access (even reading the entity) fails.

The `summary` and `findingsJson` columns in `PrAnalysis` were annotated with
both `@Lob` and `@Column(columnDefinition = "TEXT")` — redundant. `TEXT` in
PostgreSQL is not a LOB type.

### Fix

Remove `@Lob` from `summary` and `findingsJson`:

```java
@Column(columnDefinition = "TEXT")
private String summary;

@Column(name = "findings_json", columnDefinition = "TEXT")
private String findingsJson;
```

`@Column(columnDefinition = "TEXT")` alone is sufficient for the PostgreSQL
`TEXT` type and does not trigger Hibernate LOB streaming.

See commit `04fa83c`.

---

## 4. Security findings show 🟡 YELLOW instead of 🔴 RED

### Symptom

A PR with a hardcoded credential/secret gets flagged with the `security` label
and triggers an email alert, but the tier displays as **YELLOW** on the
dashboard.

### Root cause

`computeTier()` in `SummaryGenerator.java` only returned RED for the
AI-boilerplate pattern (templated + sweeping + no description). The security
flag was orthogonal — it triggered the email via `ThresholdAlertService` but
did not affect the tier.

### Fix

Add an early-return for security findings before the other tier checks:

```java
if (hasSecurityFinding) {
    return new TriageResult(TriageResult.Tier.RED, true,
            TriageResult.SuggestedAction.MANUAL_CHECK);
}
```

Also update the RED recommendation text to cover both security and
AI-boilerplate cases:

```java
case RED -> "Requires immediate review.";
```

See commit `1f5805b`.

---

## 5. Webhook payload too large error

### Symptom

GitHub deliveries fail with "HTTP 413 Payload Too Large". The bot logs:
```
Payload too large: 500000 bytes (max: 262144)
```

### Root cause

The default `MAX_PAYLOAD_BYTES` in `GitHubWebhookController` is 256 KB.
GitHub webhook payloads for PRs with large diffs can exceed this.

### Fix

Increase the threshold in `GitHubWebhookController.java`:

```java
private static final long MAX_PAYLOAD_BYTES = 1_048_576;  // 1 MB
```

Or, if you set `app.action-secret` and `app.base-url` in `application.yaml`,
remember that these are read from env vars (see the config reference).

---

## 6. Missing `app.action-secret` or `app.base-url`

### Symptom

Action links (approve/close/reject) render as `undefined?token=...` on the
dashboard, or the dashboard shows errors.

### Root cause

These config fields were missing from `application.yaml`. They are consumed
by `ActionsConfig` and `TokenService` to generate signed one-click action
URLs.

### Fix

Add to `bot/src/main/resources/application.yaml`:

```yaml
app:
  action-secret: ${APP_ACTION_SECRET:}
  base-url: ${APP_BASE_URL:}
```

Then set in your environment:

```dotenv
APP_ACTION_SECRET=random-32-byte-secret-for-hmac
APP_BASE_URL=https://your-ngrok-url.ngrok-free.dev
```

See commit `85acac2`.

---

## 7. `bot/certs/` should be gitignored

The GitHub App private key (`github-app.pem`) is secrets. Add to the
repository root `.gitignore`:

```gitignore
bot/certs/
```

See commit `469ce4d`.

---

## 8. Application name: "bot" vs "glint"

The Maven artifact is `bot` (`pom.xml` → `<artifactId>bot</artifactId>`), but
the `spring.application.name` in `application.yaml` is `glint`. This is
intentional — `glint` is the project code name, `bot` is the deployment
artifact. On the filesystem, everything lives under `bot/`.

When reading logs, config, or paths: `bot/` is the Maven module,
`glint` is the Spring application name (used in logging, metrics, etc.).

---

## Recap: full `.env` for a working setup

```dotenv
# GitHub App
GITHUB_APP_ID=123456
GITHUB_CLIENT_ID=Iv1.xxxxx
GITHUB_WEBHOOK_SECRET=********************************
GITHUB_PRIVATE_KEY_PATH=certs/github-app.pem

# LLM
LLM_MODEL=gpt-4o-mini
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-...                          # or use a different provider

# App
APP_ACTION_SECRET=random-32-byte-secret-for-hmac
APP_BASE_URL=https://your-ngrok-url.ngrok-free.dev

# Mail (Gmail)
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=abcd efgh ijkl mnop           # Gmail App Password
MAIL_FROM=you@gmail.com
MAIL_MAINTAINERS=you@gmail.com,your-team@example.com

# Spring Datasource (PostgreSQL)
DATABASE_URL=jdbc:postgresql://localhost:5432/pr_triage
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres

# Optional
HEURISTICS_ENABLED=true
LLM_ENABLED=true
AUTO_APPROVE=false
```
