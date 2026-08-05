package io.ipbreaker.wallet.infrastructure.reconciliation;

import io.ipbreaker.wallet.application.reconciliation.OnChainBalanceTarget;
import io.ipbreaker.wallet.application.reconciliation.ReconciliationDifference;
import io.ipbreaker.wallet.application.reconciliation.ReconciliationRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReconciliationRepository implements ReconciliationRepository {
    private static final String LEDGER_DIFFERENCES = """
            SELECT n.network_code, a.asset_code, la.id,
                   CASE WHEN la.account_type = 'ASSET'
                        THEN COALESCE(SUM(CASE WHEN le.direction = 'DEBIT'
                            THEN le.amount_raw ELSE -le.amount_raw END), 0)
                        ELSE COALESCE(SUM(CASE WHEN le.direction = 'CREDIT'
                            THEN le.amount_raw ELSE -le.amount_raw END), 0) END AS expected_amount,
                   COALESCE(ab.available_amount_raw, 0) AS actual_amount
            FROM ledger_account la
            JOIN asset a ON a.id = la.asset_id
            JOIN chain_network n ON n.id = a.network_id
            LEFT JOIN ledger_entry le ON le.ledger_account_id = la.id
            LEFT JOIN account_balance ab ON ab.ledger_account_id = la.id
            WHERE n.network_code = ?
            GROUP BY n.network_code, a.asset_code, la.id, la.account_type,
                     ab.available_amount_raw
            HAVING expected_amount <> actual_amount
            """;

    private static final String DEPOSIT_DIFFERENCES = """
            SELECT n.network_code, a.asset_code, d.id, d.amount_raw,
                   COALESCE(SUM(CASE
                       WHEN la.owner_type = 'SYSTEM' AND la.account_type = 'ASSET'
                            AND le.direction = 'DEBIT' THEN le.amount_raw
                       WHEN la.owner_type = 'USER' AND la.account_type = 'LIABILITY'
                            AND le.direction = 'CREDIT' THEN le.amount_raw
                       ELSE 0 END), 0) AS actual_amount,
                   COUNT(le.id) AS entry_count
            FROM deposit d
            JOIN chain_network n ON n.id = d.network_id
            JOIN asset a ON a.id = d.asset_id
            LEFT JOIN ledger_transaction lt ON lt.id = d.credited_ledger_tx_id
            LEFT JOIN ledger_entry le ON le.ledger_transaction_id = lt.id
            LEFT JOIN ledger_account la ON la.id = le.ledger_account_id
            WHERE d.status = 'CREDITED' AND n.network_code = ?
            GROUP BY n.network_code, a.asset_code, d.id, d.amount_raw
            HAVING entry_count <> 2 OR actual_amount <> d.amount_raw * 2
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcReconciliationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ReconciliationDifference> findLedgerBalanceDifferences(String networkCode) {
        return jdbcTemplate.query(LEDGER_DIFFERENCES, (resultSet, rowNumber) ->
                new ReconciliationDifference(
                        "LEDGER_BALANCE",
                        resultSet.getString("network_code"),
                        resultSet.getString("asset_code"),
                        "LEDGER_ACCOUNT",
                        Long.toString(resultSet.getLong("id")),
                        integer(resultSet.getBigDecimal("expected_amount")),
                        integer(resultSet.getBigDecimal("actual_amount")),
                        "ledger entries differ from balance snapshot"), networkCode);
    }

    @Override
    public List<ReconciliationDifference> findDepositLedgerDifferences(String networkCode) {
        return jdbcTemplate.query(DEPOSIT_DIFFERENCES, (resultSet, rowNumber) ->
                new ReconciliationDifference(
                        "DEPOSIT_LEDGER",
                        resultSet.getString("network_code"),
                        resultSet.getString("asset_code"),
                        "DEPOSIT",
                        Long.toString(resultSet.getLong("id")),
                        integer(resultSet.getBigDecimal("amount_raw")).multiply(BigInteger.TWO),
                        integer(resultSet.getBigDecimal("actual_amount")),
                        "credited deposit does not have exactly one balanced posting"), networkCode);
    }

    @Override
    public List<OnChainBalanceTarget> findOnChainBalanceTargets(String networkCode) {
        return jdbcTemplate.query("""
                SELECT n.network_code, a.asset_code, a.asset_type, a.contract_address,
                       wa.address, sc.last_scanned_block
                FROM wallet_address wa
                JOIN chain_network n ON n.id = wa.network_id
                JOIN asset a ON a.network_id = n.id
                JOIN scan_cursor sc ON sc.network_id = n.id
                WHERE n.network_code = ? AND n.status = 'ACTIVE'
                  AND a.deposit_enabled = TRUE
                  AND wa.status IN ('AVAILABLE', 'ASSIGNED')
                ORDER BY a.id, wa.id
                """, (resultSet, rowNumber) -> new OnChainBalanceTarget(
                        resultSet.getString("network_code"),
                        resultSet.getString("asset_code"),
                        resultSet.getString("asset_type"),
                        resultSet.getString("contract_address"),
                        resultSet.getString("address"),
                        resultSet.getLong("last_scanned_block")), networkCode);
    }

    @Override
    public BigInteger findPlatformLedgerBalance(String networkCode, String assetCode) {
        BigDecimal value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ab.available_amount_raw), 0)
                FROM ledger_account la
                JOIN asset a ON a.id = la.asset_id
                JOIN chain_network n ON n.id = a.network_id
                LEFT JOIN account_balance ab ON ab.ledger_account_id = la.id
                WHERE n.network_code = ? AND a.asset_code = ?
                  AND la.owner_type = 'SYSTEM' AND la.account_type = 'ASSET'
                """, BigDecimal.class, networkCode, assetCode);
        return integer(value);
    }

    @Override
    @Transactional
    public void replaceResults(
            String checkType, String networkCode, List<ReconciliationDifference> differences) {
        jdbcTemplate.update("""
                UPDATE reconciliation_difference SET status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP(6)
                WHERE check_type = ? AND network_code = ? AND status = 'OPEN'
                """, checkType, networkCode);
        for (ReconciliationDifference difference : differences) {
            jdbcTemplate.update("""
                    INSERT INTO reconciliation_difference
                        (check_type, network_code, asset_code, subject_type, subject_key,
                         expected_amount_raw, actual_amount_raw, details, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                    ON DUPLICATE KEY UPDATE
                        expected_amount_raw = VALUES(expected_amount_raw),
                        actual_amount_raw = VALUES(actual_amount_raw),
                        details = VALUES(details), status = 'OPEN',
                        occurrence_count = occurrence_count + 1,
                        last_detected_at = CURRENT_TIMESTAMP(6), resolved_at = NULL
                    """, difference.type(), difference.networkCode(), difference.assetCode(),
                    difference.subjectType(), difference.subjectKey(), difference.expectedAmount(),
                    difference.actualAmount(), difference.details());
        }
        jdbcTemplate.update("""
                INSERT INTO reconciliation_checkpoint
                    (check_type, network_code, difference_count, completed_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE difference_count = VALUES(difference_count),
                    completed_at = VALUES(completed_at)
                """, checkType, networkCode, differences.size());
    }

    private BigInteger integer(BigDecimal value) {
        return value == null ? BigInteger.ZERO : value.toBigIntegerExact();
    }
}
