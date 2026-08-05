#!/usr/bin/env bash
set -euo pipefail

api_base="${API_BASE_URL:-http://localhost:8080}"
demo_user="${DEMO_USER_ID:-interview-demo}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is missing: $1" >&2
    exit 1
  }
}

require_command curl
require_command jq

echo "1/6 Service health"
curl --fail --silent --show-error "${api_base}/actuator/health" | jq .

echo "2/6 Allocate or read the user's Sepolia deposit address"
address_response="$(curl --fail --silent --show-error \
  -X POST "${api_base}/api/v1/users/${demo_user}/deposit-addresses" \
  -H 'Content-Type: application/json' -d '{"networkCode":"SEPOLIA"}')"
echo "${address_response}" | jq .

echo "3/6 Send Sepolia ETH or a configured ERC-20 token to the returned address."
echo "Press Enter after the transaction is broadcast."
read -r

echo "4/6 Watch scanner height, lag, RPC latency, failures, and reconciliation"
curl --fail --silent --show-error "${api_base}/actuator/prometheus" \
  | grep -E '^wallet_(scanner|rpc|reconciliation)_' || true

echo "5/6 Deposit status"
curl --fail --silent --show-error \
  "${api_base}/api/v1/users/${demo_user}/deposits" | jq .

echo "6/6 Balance and ledger audit trail"
curl --fail --silent --show-error \
  "${api_base}/api/v1/users/${demo_user}/balances" | jq .
curl --fail --silent --show-error \
  "${api_base}/api/v1/users/${demo_user}/ledger-transactions" | jq .

echo "Open reconciliation differences in MySQL:"
echo "SELECT * FROM reconciliation_difference ORDER BY last_detected_at DESC;"
