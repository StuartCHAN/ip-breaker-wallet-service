# Sprint 4: Confirmations and double-entry ledger

Sprint 4 promotes detected deposits using the latest chain height and credits confirmed deposits
through an immutable double-entry ledger. A balance is a query snapshot backed by ledger entries,
not an independent source of truth.

## State machine

```text
DETECTED -> CONFIRMED -> CREDITED
```

- Confirmations are calculated as `latest height - deposit block height + 1`.
- A deposit becomes `CONFIRMED` when it reaches its network's configured confirmation requirement.
- It becomes `CREDITED` only after the ledger transaction, both entries, balance snapshots, and
  deposit update commit in one database transaction.

## Deposit posting

```text
Debit  SYSTEM / network / ASSET       amount
Credit USER / user id / LIABILITY     amount
```

Amounts remain unsigned integers in each asset's smallest unit. The posting model rejects entries
unless total debits equal total credits before any rows are inserted.

## Idempotency and concurrency

- The confirmed deposit row is locked before posting.
- Only a `CONFIRMED` deposit with no credited transaction can be posted.
- `ledger_transaction (business_type, business_id)` is unique, with `DEPOSIT + deposit id` as the
  stable business key.
- Ledger entries are unique per transaction, account, and direction.
- The final deposit update is conditional; any failure rolls the whole posting back.

These rules make repeated jobs and competing service instances safe. Database uniqueness remains
the final defense even if scheduling or application checks fail.

## APIs

```text
GET /api/v1/users/{userId}/balances
GET /api/v1/users/{userId}/ledger-transactions
```

Amounts are returned as strings to avoid JSON precision loss. Ledger transaction responses include
both the platform debit and user credit so callers can independently verify each posting's balance.
