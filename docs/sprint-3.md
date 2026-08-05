# Sprint 3: Deposit detection

Sprint 3 recognizes successful native ETH transfers and standard ERC-20 `Transfer` logs while the
safe-block scanner persists each block. Only transfers to assigned platform deposit addresses and
deposit-enabled assets become deposit records.

## Safety properties

- Failed EVM transactions never create deposits.
- Native ETH transfers use log index `-1`; zero-value transfers are ignored.
- ERC-20 transfers require the canonical `Transfer(address,address,uint256)` topic layout.
- Addresses and transaction hashes are normalized to lowercase before matching and storage.
- Native deposits are idempotent by network, transaction hash, asset, and sentinel log index.
- Token deposits are idempotent by network, transaction hash, asset, and actual log index.
- Multiple `Transfer` logs in one transaction remain distinct.
- Block persistence, deposit creation, and cursor advance share one database transaction.
- Duplicate events become no-ops; unrelated database errors are not swallowed.
- API amounts are returned as base-unit strings to avoid JSON number precision loss.

## APIs

```text
GET /api/v1/users/{userId}/deposits
GET /api/v1/deposits/{depositId}
```

The first endpoint returns newest records first. The second returns `WALLET-404-002` when the
deposit does not exist. Sprint 3 records start in `DETECTED`; confirmation and ledger crediting are
reserved for Sprint 4.

## ERC-20 asset setup

Before scanning a token, insert one deposit-enabled asset with `asset_type = 'ERC20'` and its
lowercase contract address. The scanner only recognizes configured assets; arbitrary token events
sent to a platform address are intentionally ignored.
