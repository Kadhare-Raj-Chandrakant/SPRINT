# Copy-paste this entire block at once
#!/bin/bash

PAYLOAD='{"action":"opened","pull_request":{"number":1,"head":{"sha":"abc123"},"title":"Test PR"},"repository":{"full_name":"owner/repo"},"installation":{"id":111748684}}'
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$GITHUB_WEBHOOK_SECRET" | sed 's/^.* /sha256=/')

echo "Testing webhook..."
echo "Signature: $SIGNATURE"

curl -v -X POST https://unabsorptive-kyla-slyly.ngrok-free.dev/webhook/github \
  -H "X-Hub-Signature-256: $SIGNATURE" \
  -H "X-GitHub-Event: pull_request" \
  -H "X-GitHub-Delivery: test-delivery-1" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD"

echo ""
echo "Check logs:"
echo "tail -f logs/application.log"