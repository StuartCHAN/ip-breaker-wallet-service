#!/usr/bin/env bash
set -euo pipefail

api_base="${API_BASE_URL:-http://localhost:8080}"
network="${NETWORK_CODE:-SEPOLIA}"
agreement_id="${AGREEMENT_ID:-}"
manifest_file="${TERMS_MANIFEST_FILE:-}"
poll_seconds="${POLL_SECONDS:-2}"
timeout_seconds="${TIMEOUT_SECONDS:-120}"
output_dir="${OUTPUT_DIR:-$(mktemp -d)}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command is missing: $1" >&2
    exit 1
  }
}

require_value() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "Required environment variable is missing: ${name}" >&2
    exit 1
  fi
}

run_hook() {
  local label="$1"
  local hook="$2"
  if [[ -z "${hook}" || ! -x "${hook}" ]]; then
    echo "${label} must point to an executable hook: ${hook:-<unset>}" >&2
    exit 1
  fi
  echo "Running ${label}: ${hook}"
  "${hook}"
}

api_url() {
  local suffix="$1"
  echo "${api_base}/api/v1/license-agreements/${agreement_id}/${suffix}?network=${network}"
}

wait_for_status() {
  local expected="$1"
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    local response
    response="$(curl --fail --silent --show-error "$(api_url assurance-status)" || true)"
    local status
    status="$(jq -r '.data.settlementStatus // empty' <<<"${response}")"
    if [[ "${status}" == "${expected}" ]]; then
      echo "Observed settlement status: ${expected}"
      return 0
    fi
    sleep "${poll_seconds}"
  done
  echo "Timed out waiting for settlement status ${expected}" >&2
  return 1
}

wait_for_agreement() {
  local deadline=$((SECONDS + timeout_seconds))
  while (( SECONDS < deadline )); do
    if curl --fail --silent \
        "${api_base}/api/v1/license-agreements/${agreement_id}?network=${network}" >/dev/null; then
      echo "Observed indexed license agreement: ${agreement_id}"
      return 0
    fi
    sleep "${poll_seconds}"
  done
  echo "Timed out waiting for indexed license agreement ${agreement_id}" >&2
  return 1
}

save_state() {
  local stage="$1"
  curl --fail --silent --show-error "$(api_url audit-trail)" \
    | jq . > "${output_dir}/${stage}-audit-trail.json"
  curl --fail --silent --show-error "$(api_url assurance-status)" \
    | jq . > "${output_dir}/${stage}-assurance-status.json"
  curl --fail --silent --show-error -X POST "$(api_url settlement-proof-package)" \
    | jq . > "${output_dir}/${stage}-settlement-proof.json"
}

require_command curl
require_command jq
require_command python3
require_value AGREEMENT_ID "${agreement_id}"
require_value TERMS_MANIFEST_FILE "${manifest_file}"
[[ -r "${manifest_file}" ]] || { echo "Cannot read ${manifest_file}" >&2; exit 1; }
mkdir -p "${output_dir}"

echo "Stage 1/7 — create the on-chain license agreement"
run_hook CREATE_HOOK "${CREATE_HOOK:-}"

echo "Stage 2/7 — wait for the rights index, then bind structured terms"
wait_for_agreement
curl --fail --silent --show-error -X POST \
  "${api_base}/api/v1/license-agreements/${agreement_id}/terms-manifests?network=${network}" \
  -H 'Content-Type: application/json' --data-binary "@${manifest_file}" | jq .

echo "Stage 3/7 — fund the escrow and observe eligibility plus allocation"
run_hook FUND_HOOK "${FUND_HOOK:-}"
wait_for_status SETTLED
save_state 01-settled

echo "Stage 4/7 — simulate a technical chain reorganization"
echo "The hook must orphan the eligibility/payment fact; do not use a legal dispute for this stage."
run_hook REORG_HOOK "${REORG_HOOK:-}"
wait_for_status REVERSED
save_state 02-reversed

echo "Stage 5/7 — restore the payment on the new canonical chain"
run_hook RESTORE_HOOK "${RESTORE_HOOK:-}"
wait_for_status RESTORED
save_state 03-restored

echo "Stage 6/7 — verify all journals balance independently"
python3 - "${output_dir}/03-restored-audit-trail.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    entries = json.load(source)["data"]["ledgerEntries"]
totals = {}
for entry in entries:
    debit, credit = totals.setdefault(str(entry["ledger_transaction_id"]), [0, 0])
    if entry["direction"] == "DEBIT":
        debit += int(entry["amount_raw"])
    elif entry["direction"] == "CREDIT":
        credit += int(entry["amount_raw"])
    totals[str(entry["ledger_transaction_id"])] = [debit, credit]
if not totals or any(debit != credit for debit, credit in totals.values()):
    raise SystemExit("Settlement journal balance verification failed")
PY
echo "Original, reversal, and restoration journals are independently balanced."

echo "Stage 7/7 — artifacts"
echo "Audit snapshots and proof packages: ${output_dir}"
echo "Dashboard: ${api_base}/sprint10-dashboard.html"
