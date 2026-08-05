package io.ipbreaker.wallet.infrastructure.rights;

import io.ipbreaker.wallet.rights.backfill.BackfillCursorRepository;
import io.ipbreaker.wallet.rights.contract.ManagedContract;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBackfillCursorRepository implements BackfillCursorRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcBackfillCursorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void initialize(ManagedContract contract, long targetSafeBlock) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO ip_contract_backfill_cursor (network_id, contract_id, next_block, "
                        + "target_safe_block, status) VALUES (?, ?, ?, ?, ?)",
                contract.networkId(), contract.id(), contract.deploymentBlock(), targetSafeBlock,
                contract.deploymentBlock() > targetSafeBlock ? "CAUGHT_UP" : "PENDING");
    }

    @Override
    public Optional<BackfillCursor> tryAcquire(long networkId, String owner, Duration duration) {
        Optional<BackfillCursor> candidate = jdbcTemplate.query(
                "SELECT network_id, contract_id, next_block, target_safe_block "
                        + "FROM ip_contract_backfill_cursor WHERE network_id = ? "
                        + "AND status IN ('PENDING','RUNNING') AND next_block <= target_safe_block "
                        + "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP(6) OR lease_owner = ?) "
                        + "ORDER BY next_block, contract_id LIMIT 1",
                (resultSet, rowNumber) -> new BackfillCursor(
                        resultSet.getLong("network_id"), resultSet.getLong("contract_id"),
                        resultSet.getLong("next_block"), resultSet.getLong("target_safe_block")),
                networkId, owner).stream().findFirst();
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        BackfillCursor cursor = candidate.orElseThrow();
        int changed = jdbcTemplate.update(
                "UPDATE ip_contract_backfill_cursor SET status = 'RUNNING', lease_owner = ?, "
                        + "lease_until = TIMESTAMPADD(MICROSECOND, ?, CURRENT_TIMESTAMP(6)) "
                        + "WHERE network_id = ? AND contract_id = ? AND next_block = ? "
                        + "AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP(6) OR lease_owner = ?)",
                owner, duration.toNanos() / 1_000L, networkId, cursor.contractId(),
                cursor.nextBlock(), owner);
        return changed == 1 ? candidate : Optional.empty();
    }

    @Override
    public void advance(long networkId, long contractId, String owner, long processedBlock) {
        int changed = jdbcTemplate.update(
                "UPDATE ip_contract_backfill_cursor SET next_block = ?, "
                        + "status = CASE WHEN ? >= target_safe_block THEN 'CAUGHT_UP' ELSE 'RUNNING' END "
                        + "WHERE network_id = ? AND contract_id = ? AND lease_owner = ? "
                        + "AND lease_until >= CURRENT_TIMESTAMP(6) AND next_block = ?",
                processedBlock + 1, processedBlock, networkId, contractId, owner, processedBlock);
        if (changed != 1) {
            throw new IllegalStateException("Backfill cursor lease expired or height changed");
        }
    }

    @Override
    public void release(long networkId, long contractId, String owner) {
        jdbcTemplate.update(
                "UPDATE ip_contract_backfill_cursor SET lease_owner = NULL, lease_until = NULL "
                        + "WHERE network_id = ? AND contract_id = ? AND lease_owner = ?",
                networkId, contractId, owner);
    }
}
