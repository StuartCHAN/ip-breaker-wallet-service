package io.ipbreaker.wallet.infrastructure.scan;

import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScanCursor;
import io.ipbreaker.wallet.application.scan.ScanNetwork;
import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.scan.ScannedReceipt;
import io.ipbreaker.wallet.application.scan.ScannedTransaction;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBlockScanRepository implements BlockScanRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcBlockScanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ScanNetwork> findEnabledNetwork(String networkCode) {
        return jdbcTemplate.query(
                "SELECT id, network_code, required_confirmations, scan_start_block "
                        + "FROM chain_network WHERE network_code = ? AND status = 'ACTIVE'",
                (resultSet, rowNumber) -> new ScanNetwork(
                        resultSet.getLong("id"),
                        resultSet.getString("network_code"),
                        resultSet.getInt("required_confirmations"),
                        resultSet.getLong("scan_start_block")),
                networkCode).stream().findFirst();
    }

    @Override
    @Transactional
    public ScanCursor getOrCreateCursor(ScanNetwork network) {
        long initialHeight = network.startBlock() - 1L;
        jdbcTemplate.update(
                "INSERT IGNORE INTO scan_cursor "
                        + "(network_id, last_scanned_block, last_scanned_hash) VALUES (?, ?, ?)",
                network.id(), initialHeight, zeroHash());
        return readCursor(network.id(), false);
    }

    @Override
    public boolean tryAcquireLease(long networkId, String owner, Duration duration) {
        int updated = jdbcTemplate.update(
                "UPDATE scan_cursor SET lease_owner = ?, "
                        + "lease_until = TIMESTAMPADD(MICROSECOND, ?, CURRENT_TIMESTAMP(6)) "
                        + "WHERE network_id = ? AND "
                        + "(lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP(6) OR lease_owner = ?)",
                owner, duration.toNanos() / 1_000L, networkId, owner);
        return updated == 1;
    }

    @Override
    @Transactional
    public void saveBlockAndAdvance(long networkId, String owner, ScannedBlock block) {
        ScanCursor cursor = readCursor(networkId, true);
        verifyLease(networkId, owner);
        if (block.number() != cursor.lastScannedBlock() + 1L) {
            throw new IllegalStateException("Block does not follow scan cursor");
        }
        if (!cursor.lastScannedHash().equals(zeroHash())
                && !block.parentHash().equals(cursor.lastScannedHash())) {
            throw new IllegalStateException("Block parent does not match scan cursor");
        }
        long blockId = insertBlock(networkId, block);
        for (ScannedTransaction transaction : block.transactions()) {
            long transactionId = insertTransaction(networkId, blockId, transaction);
            insertReceipt(networkId, transactionId, transaction.receipt());
        }
        int updated = jdbcTemplate.update(
                "UPDATE scan_cursor SET last_scanned_block = ?, last_scanned_hash = ?, "
                        + "version = version + 1 WHERE network_id = ? AND lease_owner = ? "
                        + "AND lease_until >= CURRENT_TIMESTAMP(6)",
                block.number(), block.hash(), networkId, owner);
        if (updated != 1) {
            throw new IllegalStateException("Scanner lease expired before cursor advance");
        }
    }

    @Override
    public void releaseLease(long networkId, String owner) {
        jdbcTemplate.update(
                "UPDATE scan_cursor SET lease_owner = NULL, lease_until = NULL "
                        + "WHERE network_id = ? AND lease_owner = ?",
                networkId, owner);
    }

    private ScanCursor readCursor(long networkId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        return jdbcTemplate.queryForObject(
                "SELECT last_scanned_block, last_scanned_hash FROM scan_cursor "
                        + "WHERE network_id = ?" + suffix,
                (resultSet, rowNumber) -> new ScanCursor(
                        resultSet.getLong("last_scanned_block"),
                        resultSet.getString("last_scanned_hash")),
                networkId);
    }

    private void verifyLease(long networkId, String owner) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scan_cursor WHERE network_id = ? AND lease_owner = ? "
                        + "AND lease_until >= CURRENT_TIMESTAMP(6)",
                Integer.class, networkId, owner);
        if (count == null || count != 1) {
            throw new IllegalStateException("Scanner lease is not held");
        }
    }

    private long insertBlock(long networkId, ScannedBlock block) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO chain_block (network_id, block_number, block_hash, parent_hash, "
                            + "block_timestamp, status, scanned_at) VALUES (?, ?, ?, ?, ?, 'SAFE', "
                            + "CURRENT_TIMESTAMP(6))",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, networkId);
            statement.setLong(2, block.number());
            statement.setString(3, block.hash());
            statement.setString(4, block.parentHash());
            statement.setTimestamp(5, Timestamp.from(block.timestamp()));
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "block");
    }

    private long insertTransaction(
            long networkId,
            long blockId,
            ScannedTransaction transaction) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO chain_transaction (network_id, block_id, tx_hash, from_address, "
                            + "to_address, nonce, value_raw, tx_status, transaction_index) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, networkId);
            statement.setLong(2, blockId);
            statement.setString(3, transaction.hash());
            statement.setString(4, transaction.fromAddress());
            statement.setString(5, transaction.toAddress());
            statement.setString(6, transaction.nonce().toString());
            statement.setString(7, transaction.value().toString());
            statement.setString(8, transaction.receipt().success() ? "SUCCESS" : "REVERTED");
            statement.setInt(9, transaction.transactionIndex());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder, "transaction");
    }

    private void insertReceipt(long networkId, long transactionId, ScannedReceipt receipt) {
        jdbcTemplate.update(
                "INSERT INTO chain_receipt (network_id, transaction_id, tx_hash, block_number, "
                        + "transaction_index, success, gas_used, contract_address) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                networkId,
                transactionId,
                receipt.transactionHash(),
                receipt.blockNumber(),
                receipt.transactionIndex(),
                receipt.success(),
                receipt.gasUsed(),
                receipt.contractAddress());
    }

    private String zeroHash() {
        return "0x0000000000000000000000000000000000000000000000000000000000000000";
    }

    private long generatedId(KeyHolder keyHolder, String recordType) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated key returned for " + recordType);
        }
        return key.longValue();
    }
}
