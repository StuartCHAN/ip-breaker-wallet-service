# Sprint 5: Chain reorganization and reversal

## Detection and recovery

The scanner validates every new block's `parentHash` against the persisted cursor hash. A
mismatch stops forward processing. The reorganization service compares locally persisted
canonical hashes with blocks returned by the RPC client, walking backwards until it finds the
highest common ancestor.

The database then performs one transaction while the scanner lease and cursor row are locked:

1. Create one `DEPOSIT_REVERSAL + depositId` ledger transaction for every credited deposit in
   the abandoned branch.
2. Copy the original two entries with their directions reversed and decrement both balance
   snapshots.
3. Mark every affected deposit `REORGED` and reset its confirmations.
4. Mark the abandoned blocks `ORPHANED` without deleting their audit history.
5. Move the cursor to the common ancestor.

The next scheduled scan starts at the ancestor's successor and follows the replacement branch.
The reversal business key is unique, so retries cannot reverse a credited deposit twice.

## Accounting rule

The original deposit is:

```text
Debit  platform blockchain asset
Credit user liability
```

The reorganization reversal is:

```text
Credit platform blockchain asset
Debit  user liability
```

Available balance snapshots may become negative after a reversal. This is intentional: a chain
reorganization must be recorded even if the user already consumed the credited funds. The
negative snapshot represents an amount the user owes; it must never block canonical-chain
recovery. Every posting still has equal debit and credit totals.

## Repeatable local test

`BlockScannerJobTest.findsCommonAncestorRollsBackAndRescansReplacementBranch` uses only in-memory
blocks. It first exposes an old branch through height 103, switches the simulated RPC client to a
replacement branch whose common ancestor is 101, and runs the scanner twice. The assertions verify
the rollback height and the ordered rescan of heights 102 through 104. No Sepolia timing or live
RPC state is involved.
