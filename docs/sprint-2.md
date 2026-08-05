# Sprint 2: Safe block scanner

Sprint 2 adds a restart-safe Sepolia block scanner. It reads blocks in height order and persists
each block, its transactions, receipts, and the scan cursor in one database transaction.

## Safety properties

- The target is `latest block - required confirmations`, not the unstable chain head.
- A database lease permits only one active scanner per network across service instances.
- The lease uses database time and expires automatically after a crashed instance stops renewing it.
- The cursor advances only after the complete block transaction commits.
- Unique keys on block height, transaction hash, and receipt hash provide storage idempotency.
- RPC calls have connection/read timeouts and bounded exponential-backoff retries.
- Each scheduled run scans at most `wallet.scanner.batch-size` blocks.

## Sepolia acceptance

Set `chain_network.scan_start_block` to the first height required by the deployment, configure
`SEPOLIA_RPC_URL`, start MySQL, and run the service. A production deployment must choose this
height explicitly; changing it after the cursor exists does not rewrite scan history. The acceptance
condition is:

```sql
SELECT c.last_scanned_block,
       n.required_confirmations
FROM scan_cursor c
JOIN chain_network n ON n.id = c.network_id
WHERE n.network_code = 'sepolia';
```

The cursor must converge to the current Sepolia head minus `required_confirmations`, remain stable
across a service restart, and continue advancing with two service instances running concurrently.
