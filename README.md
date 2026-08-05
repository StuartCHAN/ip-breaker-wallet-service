# IP Breaker Wallet Service

A modular Java wallet backend that will implement an auditable Sepolia ETH/ERC-20 deposit pipeline: address allocation, recoverable block scanning, confirmation, idempotent double-entry crediting, reorg reversal, and reconciliation.

## Sprint 4 status

Sprint 4 completes confirmed-deposit crediting with an auditable double-entry ledger and balance
snapshots on top of the address-allocation, scanning, and deposit-detection pipeline.

- Java 21 and Spring Boot 3.5
- Maven multi-module architecture
- MySQL 8.4, Redis 7.4, and Flyway
- Initial 12-table wallet schema and Sepolia seed data
- Standard API envelopes and exception handling
- Actuator health endpoints
- Checkstyle, SpotBugs, JaCoCo, and GitHub Actions
- Docker Compose one-command environment
- Concurrent address-pool allocation with row locking and unique constraints
- Idempotent deposit-address allocation and lookup APIs
- Lowercase Ethereum storage with EIP-55 API display
- `AddressProvider` and `Signer` security-boundary interfaces
- Restart-safe Sepolia block scanning with database leases and bounded RPC retries
- Native ETH and standard ERC-20 deposit recognition
- Idempotency for multiple transfer logs in the same transaction
- User deposit-list and deposit-detail APIs
- Confirmation state transitions and idempotent double-entry deposit crediting
- Ledger-backed user balance snapshots and transaction query APIs

## Modules

| Module | Responsibility |
| --- | --- |
| `ip-breaker-wallet-bootstrap` | executable application and configuration |
| `ip-breaker-wallet-api` | REST controllers, validation, and error mapping |
| `ip-breaker-wallet-application` | use-case orchestration and transaction boundaries |
| `ip-breaker-wallet-domain` | business models, state transitions, and invariants |
| `ip-breaker-wallet-chain` | Ethereum RPC adapters, block and log parsing |
| `ip-breaker-wallet-infrastructure` | MySQL, Redis, repository, and outbox adapters |
| `ip-breaker-wallet-job` | scanning, confirmation, and reconciliation jobs |
| `ip-breaker-wallet-common` | shared value types, responses, and error codes |

Dependencies point inward: infrastructure and API depend on application/domain contracts; domain does not depend on Spring or persistence.

## Prerequisites

- Docker Desktop with Docker Compose, or Java 21 plus MySQL 8 and Redis 7
- On Windows, Git should preserve the executable bit for `mvnw`; `mvnw.cmd` is also included.

## Start the complete stack

```bash
docker compose up --build
```

Then check:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/system/status
```

Stop it with `docker compose down`. Add `-v` only when you intentionally want to delete the local MySQL volume.

## Local development

Start dependencies:

```bash
docker compose up -d mysql redis
```

Run verification and the application:

```bash
./mvnw verify
./mvnw -pl ip-breaker-wallet-bootstrap -am spring-boot:run
```

Default local credentials are development-only and may be overridden with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT`. Secrets and RPC keys must never be committed.

## Database design

Flyway owns the schema under `ip-breaker-wallet-infrastructure/src/main/resources/db/migration`. Amounts use `DECIMAL(78,0)` in the database and will use `BigInteger` in Java. Chain addresses are normalized to lowercase. Reorged data will be retained and reversed, never physically deleted.

The initial schema includes networks, assets, addresses, blocks, transactions, deposits, double-entry ledger records, balance snapshots, scan cursors, and transactional outbox events.

## Roadmap

1. Sprint 1: network, asset, and concurrent deposit-address allocation
2. Sprint 2: recoverable Sepolia block scanner
3. Sprint 3: native ETH and ERC-20 deposit recognition
4. Sprint 4: confirmation state machine and double-entry ledger
5. Sprint 5: reorg detection, rollback, and ledger reversal
6. Sprint 6: reconciliation, observability, and demonstration scripts

See [Sprint 4 acceptance checklist](docs/sprint-4.md) for the current definition of done.
