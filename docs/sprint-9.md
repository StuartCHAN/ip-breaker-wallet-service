# Sprint 9 — Fund allocation, ledger posting, reversal, and restoration

Sprint 9 consumes an immutable canonical `SettlementEligibilitySnapshot`. It creates a deterministic
allocation plan, posts a balanced journal, and handles technical chain-reorganization reversals and
canonical restoration. It does not adjudicate legal invalidity, issue refunds, move on-chain funds,
or create the Sprint 10 assurance package.

## Allocation policy

Terms schema v1 binds one `payee` and no multi-party split. Policy `PAYEE_100_V1` therefore allocates
100% of the integer base-unit amount to that payee. No platform fee or uncommitted recipient is
invented. The immutable plan records its eligibility snapshot, amount, recipient, policy version,
and hash. A future split policy must first be added to the structured terms and covered by
`termsHash`.

## Posting model

An eligible obligation may post only when:

- its current eligibility snapshot is canonical and `ELIGIBLE`;
- `settlement_status` is `ELIGIBLE` or `REVERSED`;
- `control_status` is `CLEAR`.

The journal debits the system escrow asset account and credits the payee liability account. Every
journal is balanced before commit. Both the escrow asset balance and payee liability balance are
changed in the same database transaction. Business keys make duplicate API calls and duplicate
event replay idempotent.

```text
initial:     SETTLEMENT          debit escrow asset / credit payee liability
reorg:       SETTLEMENT_REVERSAL credit escrow asset / debit payee liability
restoration: SETTLEMENT_RESTORE  debit escrow asset / credit payee liability
```

`SETTLED`, `REVERSED`, and `RESTORED` records are immutable. Reversal links to the posting it negates;
restoration links to the reversal that it restores. The original journal is never edited or deleted.

## Legal and control boundary

Only a chain reorganization that orphans the eligibility snapshot causes automatic technical
reversal. Later rights transfer, terms replacement, evidence change, dispute, court order, or manual
risk control does not retroactively reverse a posted settlement. `HELD` and `DISPUTED` block new
posting while preserving the historical settlement status and journal.

The current product has no withdrawal path, so an automatic reversal can safely require the payee's
credited available balance to remain present. A future withdrawal sprint must add reserved balances
or a recovery-receivable policy before allowing withdrawal of reorg-sensitive credits.

## API

```http
POST /api/v1/license-agreements/{agreementId}/settlement?network=SEPOLIA
GET  /api/v1/license-agreements/{agreementId}/settlement?network=SEPOLIA
GET  /api/v1/license-agreements/settlements/{settlementId}/journals
```

Canonical events also attempt posting automatically after Sprint 8 eligibility evaluation. Manual
POST is an idempotent recovery/operations endpoint, not a way to bypass eligibility or control.

## Database additions

Flyway V11 adds `settlement_allocation_plan`, `settlement_allocation_line`, and `settlement_record`,
and extends `payment_obligation.settlement_status` with `SETTLED`, `REVERSED`, and `RESTORED`.

## Verification

Run `./mvnw verify` with Java 21 and MySQL 8. Acceptance must cover balanced journals, exact integer
allocation, duplicate posting, held/disputed rejection, reorg reversal, canonical restoration,
immutable linkage, insufficient-balance rollback, and preservation of historical settlement after a
non-reorg rights or control change.
