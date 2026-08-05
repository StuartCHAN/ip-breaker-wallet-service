package io.ipbreaker.wallet.infrastructure.deposit;

import io.ipbreaker.wallet.application.deposit.DepositCandidate;
import io.ipbreaker.wallet.application.deposit.DepositRepository;
import io.ipbreaker.wallet.domain.deposit.Deposit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDepositRepository implements DepositRepository {
    private static final String SELECT_DEPOSIT = """
            SELECT d.id, cn.network_code, a.asset_code, a.asset_type, a.symbol, a.decimals,
                   d.user_id, d.tx_hash, d.log_index, d.from_address, d.to_address,
                   d.amount_raw, d.block_number, d.confirmations, d.status, d.detected_at
            FROM deposit d
            JOIN chain_network cn ON cn.id = d.network_id
            JOIN asset a ON a.id = d.asset_id
            """;

    private static final String INSERT_MATCHING = """
            INSERT INTO deposit
                (network_id, asset_id, user_id, wallet_address_id, block_id, tx_hash,
                 log_index, from_address, to_address, amount_raw, block_number,
                 confirmations, status, detected_at)
            SELECT ?, a.id, wa.user_id, wa.id, b.id, ?, ?, ?, ?, ?, ?, 0, 'DETECTED',
                   CURRENT_TIMESTAMP(6)
            FROM asset a
            JOIN wallet_address wa
              ON wa.network_id = a.network_id
             AND wa.address = ?
             AND wa.address_type = 'DEPOSIT'
             AND wa.status = 'ASSIGNED'
             AND wa.user_id IS NOT NULL
            JOIN chain_block b
              ON b.network_id = a.network_id AND b.block_number = ?
             AND b.status <> 'ORPHANED'
            WHERE a.network_id = ?
              AND a.deposit_enabled = TRUE
              AND a.asset_type = ?
              AND ((? = 'NATIVE' AND a.contract_address IS NULL)
                   OR (? = 'ERC20' AND a.contract_address = ?))
            ON DUPLICATE KEY UPDATE
                block_id = IF(status = 'REORGED' AND credited_ledger_tx_id IS NULL,
                              VALUES(block_id), block_id),
                block_number = IF(status = 'REORGED' AND credited_ledger_tx_id IS NULL,
                                  VALUES(block_number), block_number),
                confirmations = IF(status = 'REORGED' AND credited_ledger_tx_id IS NULL,
                                   0, confirmations),
                detected_at = IF(status = 'REORGED' AND credited_ledger_tx_id IS NULL,
                                 CURRENT_TIMESTAMP(6), detected_at),
                status = IF(status = 'REORGED' AND credited_ledger_tx_id IS NULL,
                            'DETECTED', status)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDepositRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertMatching(long networkId, DepositCandidate candidate) {
        jdbcTemplate.update(
                INSERT_MATCHING,
                networkId,
                candidate.transactionHash(),
                candidate.logIndex(),
                candidate.fromAddress(),
                candidate.toAddress(),
                candidate.amountRaw().toString(),
                candidate.blockNumber(),
                candidate.toAddress(),
                candidate.blockNumber(),
                networkId,
                candidate.assetType(),
                candidate.assetType(),
                candidate.assetType(),
                candidate.contractAddress());
    }

    @Override
    public List<Deposit> findByUserId(String userId) {
        return jdbcTemplate.query(
                SELECT_DEPOSIT + " WHERE d.user_id = ? ORDER BY d.id DESC",
                this::map,
                userId);
    }

    @Override
    public Optional<Deposit> findById(long depositId) {
        return jdbcTemplate.query(
                SELECT_DEPOSIT + " WHERE d.id = ?",
                this::map,
                depositId).stream().findFirst();
    }

    private Deposit map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Deposit(
                resultSet.getLong("id"),
                resultSet.getString("network_code"),
                resultSet.getString("asset_code"),
                resultSet.getString("asset_type"),
                resultSet.getString("symbol"),
                resultSet.getInt("decimals"),
                resultSet.getString("user_id"),
                resultSet.getString("tx_hash"),
                resultSet.getInt("log_index"),
                resultSet.getString("from_address"),
                resultSet.getString("to_address"),
                resultSet.getBigDecimal("amount_raw").toBigIntegerExact(),
                resultSet.getLong("block_number"),
                resultSet.getInt("confirmations"),
                resultSet.getString("status"),
                resultSet.getTimestamp("detected_at").toInstant());
    }
}
