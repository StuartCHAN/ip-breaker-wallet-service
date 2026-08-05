package io.ipbreaker.wallet.infrastructure.ledger;

import io.ipbreaker.wallet.application.ledger.ConfirmedDeposit;
import io.ipbreaker.wallet.application.ledger.LedgerRepository;
import io.ipbreaker.wallet.domain.ledger.Balance;
import io.ipbreaker.wallet.domain.ledger.LedgerEntry;
import io.ipbreaker.wallet.domain.ledger.LedgerTransaction;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {
    private static final String UPDATE_CONFIRMATIONS = """
            UPDATE deposit d
            JOIN chain_network n ON n.id = d.network_id
            SET d.confirmations = LEAST(2147483647, ? - d.block_number + 1),
                d.confirmed_at = CASE
                    WHEN d.status = 'DETECTED'
                     AND ? - d.block_number + 1 >= n.required_confirmations
                    THEN CURRENT_TIMESTAMP(6)
                    ELSE d.confirmed_at
                END,
                d.status = CASE
                    WHEN d.status = 'DETECTED'
                     AND ? - d.block_number + 1 >= n.required_confirmations
                    THEN 'CONFIRMED'
                    ELSE d.status
                END
            WHERE d.block_number <= ?
              AND n.network_code = ?
              AND d.status IN ('DETECTED', 'CONFIRMED', 'CREDITED')
            """;

    private static final String LOCK_CONFIRMED_DEPOSIT = """
            SELECT d.id, d.asset_id, n.network_code, d.user_id, d.amount_raw
            FROM deposit d
            JOIN chain_network n ON n.id = d.network_id
            WHERE d.id = ? AND d.status = 'CONFIRMED' AND d.credited_ledger_tx_id IS NULL
            FOR UPDATE
            """;

    private static final String INSERT_ACCOUNT = """
            INSERT INTO ledger_account (owner_type, owner_id, asset_id, account_type, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """;

    private static final String SELECT_ACCOUNT = """
            SELECT id FROM ledger_account
            WHERE owner_type = ? AND owner_id = ? AND asset_id = ? AND account_type = ?
            """;

    private static final String SELECT_TRANSACTIONS = """
            SELECT DISTINCT lt.id, lt.business_type, lt.business_id, lt.reference_no,
                   lt.status, a.asset_code, lt.created_at
            FROM ledger_transaction lt
            JOIN ledger_entry le ON le.ledger_transaction_id = lt.id
            JOIN ledger_account la ON la.id = le.ledger_account_id
            JOIN asset a ON a.id = la.asset_id
            WHERE la.owner_type = 'USER' AND la.owner_id = ?
            ORDER BY lt.id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void updateDepositConfirmations(String networkCode, long latestBlockNumber) {
        jdbcTemplate.update(
                UPDATE_CONFIRMATIONS,
                latestBlockNumber,
                latestBlockNumber,
                latestBlockNumber,
                latestBlockNumber,
                networkCode.toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public List<Long> findConfirmedDepositIds(String networkCode, int limit) {
        return jdbcTemplate.queryForList(
                "SELECT d.id FROM deposit d JOIN chain_network n ON n.id = d.network_id "
                        + "WHERE n.network_code = ? AND d.status = 'CONFIRMED' "
                        + "AND d.credited_ledger_tx_id IS NULL ORDER BY d.id LIMIT ?",
                Long.class,
                networkCode.toUpperCase(java.util.Locale.ROOT),
                limit);
    }

    @Override
    public Optional<ConfirmedDeposit> lockConfirmedDeposit(long depositId) {
        return jdbcTemplate.query(
                LOCK_CONFIRMED_DEPOSIT,
                (resultSet, rowNumber) -> new ConfirmedDeposit(
                        resultSet.getLong("id"),
                        resultSet.getLong("asset_id"),
                        resultSet.getString("network_code"),
                        resultSet.getString("user_id"),
                        resultSet.getBigDecimal("amount_raw").toBigIntegerExact()),
                depositId).stream().findFirst();
    }

    @Override
    public long getOrCreatePlatformAssetAccount(ConfirmedDeposit deposit) {
        return getOrCreateAccount("SYSTEM", deposit.networkCode(), deposit.assetId(), "ASSET");
    }

    @Override
    public long getOrCreateUserLiabilityAccount(ConfirmedDeposit deposit) {
        return getOrCreateAccount("USER", deposit.userId(), deposit.assetId(), "LIABILITY");
    }

    @Override
    public long createDepositLedgerTransaction(long depositId) {
        String reference = "DEPOSIT:" + depositId;
        jdbcTemplate.update(
                "INSERT INTO ledger_transaction "
                        + "(business_type, business_id, reference_no, status, description) "
                        + "VALUES ('DEPOSIT', ?, ?, 'POSTED', 'Confirmed blockchain deposit')",
                depositId,
                reference);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_transaction WHERE business_type = 'DEPOSIT' AND business_id = ?",
                Long.class,
                depositId);
    }

    @Override
    public void insertEntry(
            long transactionId, long accountId, String direction, String amountRaw) {
        jdbcTemplate.update(
                "INSERT INTO ledger_entry "
                        + "(ledger_transaction_id, ledger_account_id, direction, amount_raw) "
                        + "VALUES (?, ?, ?, ?)",
                transactionId,
                accountId,
                direction,
                amountRaw);
    }

    @Override
    public void increaseAvailableBalance(long accountId, String amountRaw) {
        jdbcTemplate.update(
                "INSERT INTO account_balance "
                        + "(ledger_account_id, available_amount_raw, pending_amount_raw, version) "
                        + "VALUES (?, ?, 0, 1) ON DUPLICATE KEY UPDATE "
                        + "available_amount_raw = available_amount_raw + VALUES(available_amount_raw), "
                        + "version = version + 1",
                accountId,
                amountRaw);
    }

    @Override
    public boolean markDepositCredited(long depositId, long transactionId) {
        return jdbcTemplate.update(
                "UPDATE deposit SET status = 'CREDITED', credited_ledger_tx_id = ?, "
                        + "credited_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE id = ? AND status = 'CONFIRMED' AND credited_ledger_tx_id IS NULL",
                transactionId,
                depositId) == 1;
    }

    @Override
    public List<Balance> findBalances(String userId) {
        return jdbcTemplate.query(
                """
                SELECT la.owner_id, n.network_code, a.asset_code, a.symbol, a.decimals,
                       ab.available_amount_raw, ab.pending_amount_raw, ab.version, ab.updated_at
                FROM ledger_account la
                JOIN asset a ON a.id = la.asset_id
                JOIN chain_network n ON n.id = a.network_id
                JOIN account_balance ab ON ab.ledger_account_id = la.id
                WHERE la.owner_type = 'USER' AND la.owner_id = ?
                  AND la.account_type = 'LIABILITY' AND la.status = 'ACTIVE'
                ORDER BY a.asset_code
                """,
                this::mapBalance,
                userId);
    }

    @Override
    public List<LedgerTransaction> findTransactions(String userId) {
        return jdbcTemplate.query(
                SELECT_TRANSACTIONS,
                (resultSet, rowNumber) -> new LedgerTransaction(
                        resultSet.getLong("id"),
                        resultSet.getString("business_type"),
                        resultSet.getLong("business_id"),
                        resultSet.getString("reference_no"),
                        resultSet.getString("status"),
                        resultSet.getString("asset_code"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        findEntries(resultSet.getLong("id"))),
                userId);
    }

    private long getOrCreateAccount(
            String ownerType, String ownerId, long assetId, String accountType) {
        jdbcTemplate.update(INSERT_ACCOUNT, ownerType, ownerId, assetId, accountType);
        return jdbcTemplate.queryForObject(
                SELECT_ACCOUNT,
                Long.class,
                ownerType,
                ownerId,
                assetId,
                accountType);
    }

    private Balance mapBalance(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Balance(
                resultSet.getString("owner_id"),
                resultSet.getString("network_code"),
                resultSet.getString("asset_code"),
                resultSet.getString("symbol"),
                resultSet.getInt("decimals"),
                resultSet.getBigDecimal("available_amount_raw").toBigIntegerExact(),
                resultSet.getBigDecimal("pending_amount_raw").toBigIntegerExact(),
                resultSet.getLong("version"),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private List<LedgerEntry> findEntries(long transactionId) {
        return jdbcTemplate.query(
                """
                SELECT le.ledger_account_id, la.owner_type, la.owner_id, la.account_type,
                       le.direction, le.amount_raw
                FROM ledger_entry le
                JOIN ledger_account la ON la.id = le.ledger_account_id
                WHERE le.ledger_transaction_id = ? ORDER BY le.id
                """,
                (resultSet, rowNumber) -> new LedgerEntry(
                        resultSet.getLong("ledger_account_id"),
                        resultSet.getString("owner_type"),
                        resultSet.getString("owner_id"),
                        resultSet.getString("account_type"),
                        resultSet.getString("direction"),
                        new BigInteger(resultSet.getString("amount_raw"))),
                transactionId);
    }
}
