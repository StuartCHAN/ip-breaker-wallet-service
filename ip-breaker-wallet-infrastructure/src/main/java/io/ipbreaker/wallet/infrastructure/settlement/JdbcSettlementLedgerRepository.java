package io.ipbreaker.wallet.infrastructure.settlement;

import io.ipbreaker.wallet.application.settlement.SettlementJournalView;
import io.ipbreaker.wallet.application.settlement.SettlementLedgerRepository;
import io.ipbreaker.wallet.application.settlement.SettlementView;
import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@Repository
public class JdbcSettlementLedgerRepository implements SettlementLedgerRepository {
    private static final String POLICY_VERSION = "PAYEE_100_V1";

    private final JdbcTemplate jdbcTemplate;

    public JdbcSettlementLedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SettlementView> postEligible(String networkCode, BigInteger agreementId) {
        Optional<PostingInput> input = findPostingInput(networkCode, agreementId, true);
        if (input.isEmpty() || !postable(input.get())) {
            return Optional.empty();
        }
        post(input.get(), null);
        return find(networkCode, agreementId);
    }

    @Override
    public void settleOrRestore(ChainDomainEvent trigger) {
        List<PostingInput> inputs = jdbcTemplate.query(
                postingInputSql("o.network_id = ? AND o.agreement_id = ?") + " FOR UPDATE",
                this::mapPostingInput, trigger.networkId(), trigger.aggregateId().toString());
        for (PostingInput input : inputs) {
            if (postable(input)) {
                post(input, trigger);
            }
        }
    }

    @Override
    public void reverseOrphaned(long networkId, long ancestorBlock, String ancestorBlockHash) {
        List<Long> originalIds = jdbcTemplate.queryForList(
                "SELECT sr.id FROM settlement_record sr "
                        + "JOIN settlement_allocation_plan ap ON ap.id = sr.allocation_plan_id "
                        + "JOIN settlement_eligibility_snapshot s ON s.id = ap.eligibility_snapshot_id "
                        + "WHERE sr.obligation_id IN (SELECT id FROM payment_obligation WHERE network_id = ?) "
                        + "AND sr.settlement_status IN ('SETTLED', 'RESTORED') "
                        + "AND sr.reversed_by_settlement_id IS NULL AND s.canonical_status = 'ORPHANED' "
                        + "ORDER BY sr.id FOR UPDATE",
                Long.class, networkId);
        for (Long originalId : originalIds) {
            reverse(originalId, ancestorBlock, ancestorBlockHash);
        }
    }

    @Override
    public Optional<SettlementView> find(String networkCode, BigInteger agreementId) {
        return jdbcTemplate.query(
                "SELECT sr.id, sr.obligation_id, n.network_code, o.agreement_id, "
                        + "sr.settlement_status, o.control_status, ap.id allocation_plan_id, "
                        + "ap.policy_version, ap.plan_hash, ap.total_amount_raw, sr.original_settlement_id, "
                        + "sr.reversed_by_settlement_id, sr.restored_from_reversal_id, "
                        + "sr.ledger_transaction_id, sr.safe_block_number, sr.safe_block_hash, sr.created_at "
                        + "FROM settlement_record sr JOIN payment_obligation o ON o.id = sr.obligation_id "
                        + "JOIN chain_network n ON n.id = o.network_id "
                        + "JOIN settlement_allocation_plan ap ON ap.id = sr.allocation_plan_id "
                        + "WHERE n.network_code = ? AND o.agreement_id = ? ORDER BY sr.id DESC LIMIT 1",
                this::mapSettlement, networkCode.toUpperCase(Locale.ROOT), agreementId.toString())
                .stream().findFirst();
    }

    @Override
    public List<SettlementJournalView> journals(long settlementId) {
        Long obligationId = jdbcTemplate.queryForObject(
                "SELECT obligation_id FROM settlement_record WHERE id = ?", Long.class, settlementId);
        if (obligationId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT sr.id, sr.settlement_status, lt.id ledger_transaction_id, lt.reference_no, "
                        + "lt.created_at FROM settlement_record sr JOIN ledger_transaction lt "
                        + "ON lt.id = sr.ledger_transaction_id WHERE sr.obligation_id = ? ORDER BY sr.id",
                (resultSet, rowNumber) -> new SettlementJournalView(
                        resultSet.getLong("id"), resultSet.getString("settlement_status"),
                        resultSet.getLong("ledger_transaction_id"), resultSet.getString("reference_no"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        entries(resultSet.getLong("ledger_transaction_id"))),
                obligationId);
    }

    private void post(PostingInput input, ChainDomainEvent trigger) {
        Previous previous = previous(input.obligationId());
        String businessType = previous.reversalId() == null ? "SETTLEMENT" : "SETTLEMENT_RESTORE";
        long businessId = previous.reversalId() == null ? input.obligationId() : previous.reversalId();
        if (ledgerTransaction(businessType, businessId).isPresent()) {
            return;
        }
        long planId = getOrCreatePlan(input);
        long transactionId = createLedgerTransaction(
                businessType, businessId, businessType + ":" + businessId,
                previous.reversalId() == null ? "Eligible license settlement" : "Restored canonical settlement");
        long systemAccount = getOrCreateAccount(
                "SYSTEM", "ESCROW:" + input.escrowAddress(), input.assetId(), "ASSET");
        long recipientAccount = getOrCreateAccount(
                "USER", input.payee(), input.assetId(), "LIABILITY");
        insertEntry(transactionId, systemAccount, "DEBIT", input.amount());
        insertEntry(transactionId, recipientAccount, "CREDIT", input.amount());
        increaseAvailable(systemAccount, input.amount());
        increaseAvailable(recipientAccount, input.amount());
        String status = previous.reversalId() == null ? "SETTLED" : "RESTORED";
        jdbcTemplate.update(
                "INSERT INTO settlement_record (obligation_id, allocation_plan_id, settlement_status, "
                        + "original_settlement_id, restored_from_reversal_id, ledger_transaction_id, "
                        + "trigger_event_id, safe_block_number, safe_block_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                input.obligationId(), planId, status,
                previous.originalId(), previous.reversalId(), transactionId,
                trigger == null ? input.paymentEventId() : trigger.id(),
                input.safeBlockNumber(), input.safeBlockHash());
        jdbcTemplate.update(
                "UPDATE payment_obligation SET settlement_status = ?, version = version + 1 WHERE id = ?",
                status, input.obligationId());
    }

    private void reverse(long originalId, long ancestorBlock, String ancestorBlockHash) {
        SettlementSource source = jdbcTemplate.queryForObject(
                "SELECT sr.id, sr.obligation_id, sr.allocation_plan_id, sr.ledger_transaction_id "
                        + "FROM settlement_record sr WHERE sr.id = ? AND sr.reversed_by_settlement_id IS NULL "
                        + "FOR UPDATE",
                (resultSet, rowNumber) -> new SettlementSource(
                        resultSet.getLong("id"), resultSet.getLong("obligation_id"),
                        resultSet.getLong("allocation_plan_id"), resultSet.getLong("ledger_transaction_id")),
                originalId);
        if (source == null) {
            return;
        }
        long transactionId = createLedgerTransaction(
                "SETTLEMENT_REVERSAL", originalId, "SETTLEMENT_REVERSAL:" + originalId,
                "Technical chain reorganization reversal");
        List<LedgerLine> lines = ledgerLines(source.ledgerTransactionId());
        verifyBalanced(lines);
        for (LedgerLine line : lines) {
            String direction = "DEBIT".equals(line.direction()) ? "CREDIT" : "DEBIT";
            insertEntry(transactionId, line.accountId(), direction, line.amount());
            decreaseAvailable(line.accountId(), line.amount());
        }
        jdbcTemplate.update(
                "INSERT INTO settlement_record (obligation_id, allocation_plan_id, settlement_status, "
                        + "original_settlement_id, ledger_transaction_id, safe_block_number, safe_block_hash) "
                        + "VALUES (?, ?, 'REVERSED', ?, ?, ?, ?)",
                source.obligationId(), source.planId(), source.id(), transactionId,
                ancestorBlock, ancestorBlockHash);
        Long reversalId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "UPDATE settlement_record SET reversed_by_settlement_id = ? WHERE id = ?",
                reversalId, source.id());
        jdbcTemplate.update(
                "UPDATE payment_obligation SET settlement_status = 'REVERSED', version = version + 1 "
                        + "WHERE id = ?",
                source.obligationId());
    }

    private Optional<PostingInput> findPostingInput(
            String networkCode, BigInteger agreementId, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        return jdbcTemplate.query(
                postingInputSql("n.network_code = ? AND o.agreement_id = ?") + suffix,
                this::mapPostingInput, networkCode.toUpperCase(Locale.ROOT), agreementId.toString())
                .stream().findFirst();
    }

    private String postingInputSql(String predicate) {
        return "SELECT o.id obligation_id, o.network_id, o.escrow_address, o.agreement_id, "
                + "o.payee, o.amount_raw, o.settlement_status, o.control_status, o.matched_payment_event_id, "
                + "s.id snapshot_id, s.safe_block_number, s.safe_block_hash, s.eligibility_decision, "
                + "s.canonical_status, a.id asset_id FROM payment_obligation o "
                + "JOIN chain_network n ON n.id = o.network_id JOIN asset a "
                + "ON a.network_id = o.network_id AND a.asset_type = 'NATIVE' "
                + "JOIN settlement_eligibility_snapshot s ON s.id = o.current_snapshot_id WHERE "
                + predicate;
    }

    private PostingInput mapPostingInput(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PostingInput(
                resultSet.getLong("obligation_id"), resultSet.getLong("network_id"),
                resultSet.getString("escrow_address"),
                new BigInteger(resultSet.getString("agreement_id")), resultSet.getLong("asset_id"),
                resultSet.getString("payee"), new BigInteger(resultSet.getString("amount_raw")),
                resultSet.getString("settlement_status"), resultSet.getString("control_status"),
                resultSet.getLong("snapshot_id"), resultSet.getLong("safe_block_number"),
                resultSet.getString("safe_block_hash"), resultSet.getString("eligibility_decision"),
                resultSet.getString("canonical_status"), nullableLong(resultSet, "matched_payment_event_id"));
    }

    private boolean postable(PostingInput input) {
        return ("ELIGIBLE".equals(input.settlementStatus()) || "REVERSED".equals(input.settlementStatus()))
                && "CLEAR".equals(input.controlStatus()) && "ELIGIBLE".equals(input.decision())
                && "CANONICAL".equals(input.canonicalStatus());
    }

    private long getOrCreatePlan(PostingInput input) {
        String material = input.escrowAddress() + "|" + input.agreementId() + "|"
                + input.safeBlockHash() + "|" + input.paymentEventId() + "|" + input.payee()
                + "|" + input.amount() + "|" + POLICY_VERSION;
        String hash = Numeric.toHexString(Hash.sha3(material.getBytes(StandardCharsets.UTF_8)))
                .toLowerCase(Locale.ROOT);
        jdbcTemplate.update(
                "INSERT IGNORE INTO settlement_allocation_plan (obligation_id, eligibility_snapshot_id, "
                        + "currency_kind, total_amount_raw, policy_version, plan_hash) "
                        + "VALUES (?, ?, 'NATIVE', ?, ?, ?)",
                input.obligationId(), input.snapshotId(), input.amount().toString(), POLICY_VERSION, hash);
        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM settlement_allocation_plan WHERE eligibility_snapshot_id = ?",
                Long.class, input.snapshotId());
        if (planId == null) {
            throw new IllegalStateException("Allocation plan was not created");
        }
        jdbcTemplate.update(
                "INSERT IGNORE INTO settlement_allocation_line "
                        + "(allocation_plan_id, line_number, recipient, amount_raw) VALUES (?, 1, ?, ?)",
                planId, input.payee(), input.amount().toString());
        return planId;
    }

    private Previous previous(long obligationId) {
        return jdbcTemplate.query(
                "SELECT original.id original_id, reversal.id reversal_id FROM settlement_record original "
                        + "LEFT JOIN settlement_record reversal ON reversal.id = original.reversed_by_settlement_id "
                        + "WHERE original.obligation_id = ? AND original.settlement_status IN ('SETTLED', 'RESTORED') "
                        + "ORDER BY original.id DESC LIMIT 1",
                (resultSet, rowNumber) -> new Previous(
                        resultSet.getLong("original_id"), nullableLong(resultSet, "reversal_id")),
                obligationId).stream().findFirst().orElse(new Previous(null, null));
    }

    private long getOrCreateAccount(String ownerType, String ownerId, long assetId, String accountType) {
        jdbcTemplate.update(
                "INSERT INTO ledger_account (owner_type, owner_id, asset_id, account_type, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE') ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)",
                ownerType, ownerId, assetId, accountType);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM ledger_account WHERE owner_type = ? AND owner_id = ? "
                        + "AND asset_id = ? AND account_type = ?",
                Long.class, ownerType, ownerId, assetId, accountType);
        if (id == null) {
            throw new IllegalStateException("Ledger account was not created");
        }
        return id;
    }

    private long createLedgerTransaction(
            String businessType, long businessId, String reference, String description) {
        jdbcTemplate.update(
                "INSERT INTO ledger_transaction (business_type, business_id, reference_no, status, description) "
                        + "VALUES (?, ?, ?, 'POSTED', ?)",
                businessType, businessId, reference, description);
        return ledgerTransaction(businessType, businessId).orElseThrow();
    }

    private Optional<Long> ledgerTransaction(String businessType, long businessId) {
        return jdbcTemplate.query(
                "SELECT id FROM ledger_transaction WHERE business_type = ? AND business_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong("id"), businessType, businessId)
                .stream().findFirst();
    }

    private void insertEntry(long transactionId, long accountId, String direction, BigInteger amount) {
        jdbcTemplate.update(
                "INSERT INTO ledger_entry (ledger_transaction_id, ledger_account_id, direction, amount_raw) "
                        + "VALUES (?, ?, ?, ?)",
                transactionId, accountId, direction, amount.toString());
    }

    private void increaseAvailable(long accountId, BigInteger amount) {
        jdbcTemplate.update(
                "INSERT INTO account_balance (ledger_account_id, available_amount_raw, pending_amount_raw, version) "
                        + "VALUES (?, ?, 0, 1) ON DUPLICATE KEY UPDATE "
                        + "available_amount_raw = available_amount_raw + VALUES(available_amount_raw), "
                        + "version = version + 1",
                accountId, amount.toString());
    }

    private void decreaseAvailable(long accountId, BigInteger amount) {
        int updated = jdbcTemplate.update(
                "UPDATE account_balance SET available_amount_raw = available_amount_raw - ?, "
                        + "version = version + 1 WHERE ledger_account_id = ? "
                        + "AND available_amount_raw >= ?",
                amount.toString(), accountId, amount.toString());
        if (updated != 1) {
            throw new IllegalStateException("Recipient balance is insufficient for technical reversal");
        }
    }

    private List<LedgerLine> ledgerLines(long transactionId) {
        return jdbcTemplate.query(
                "SELECT le.ledger_account_id, le.direction, le.amount_raw FROM ledger_entry le "
                        + "WHERE le.ledger_transaction_id = ? ORDER BY le.id",
                (resultSet, rowNumber) -> new LedgerLine(
                        resultSet.getLong("ledger_account_id"), resultSet.getString("direction"),
                        new BigInteger(resultSet.getString("amount_raw"))),
                transactionId);
    }

    private void verifyBalanced(List<LedgerLine> lines) {
        BigInteger debits = lines.stream().filter(line -> "DEBIT".equals(line.direction()))
                .map(LedgerLine::amount).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger credits = lines.stream().filter(line -> "CREDIT".equals(line.direction()))
                .map(LedgerLine::amount).reduce(BigInteger.ZERO, BigInteger::add);
        if (lines.size() < 2 || !debits.equals(credits)) {
            throw new IllegalStateException("Source settlement journal is not balanced");
        }
    }

    private SettlementView mapSettlement(ResultSet resultSet, int rowNumber) throws SQLException {
        long planId = resultSet.getLong("allocation_plan_id");
        return new SettlementView(
                resultSet.getLong("id"), resultSet.getLong("obligation_id"),
                resultSet.getString("network_code"),
                new BigInteger(resultSet.getString("agreement_id")),
                resultSet.getString("settlement_status"), resultSet.getString("control_status"),
                planId, resultSet.getString("policy_version"), resultSet.getString("plan_hash"),
                new BigInteger(resultSet.getString("total_amount_raw")), allocations(planId),
                nullableLong(resultSet, "original_settlement_id"),
                nullableLong(resultSet, "reversed_by_settlement_id"),
                nullableLong(resultSet, "restored_from_reversal_id"),
                resultSet.getLong("ledger_transaction_id"), resultSet.getLong("safe_block_number"),
                resultSet.getString("safe_block_hash"), resultSet.getTimestamp("created_at").toInstant());
    }

    private List<SettlementView.AllocationView> allocations(long planId) {
        return jdbcTemplate.query(
                "SELECT line_number, recipient, amount_raw FROM settlement_allocation_line "
                        + "WHERE allocation_plan_id = ? ORDER BY line_number",
                (resultSet, rowNumber) -> new SettlementView.AllocationView(
                        resultSet.getInt("line_number"), resultSet.getString("recipient"),
                        new BigInteger(resultSet.getString("amount_raw"))), planId);
    }

    private List<SettlementJournalView.Entry> entries(long transactionId) {
        return jdbcTemplate.query(
                "SELECT la.owner_type, la.owner_id, la.account_type, le.direction, le.amount_raw "
                        + "FROM ledger_entry le JOIN ledger_account la ON la.id = le.ledger_account_id "
                        + "WHERE le.ledger_transaction_id = ? ORDER BY le.id",
                (resultSet, rowNumber) -> new SettlementJournalView.Entry(
                        resultSet.getString("owner_type"), resultSet.getString("owner_id"),
                        resultSet.getString("account_type"), resultSet.getString("direction"),
                        new BigInteger(resultSet.getString("amount_raw"))), transactionId);
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record PostingInput(long obligationId, long networkId, String escrowAddress,
            BigInteger agreementId, long assetId, String payee, BigInteger amount, String settlementStatus,
            String controlStatus, long snapshotId, long safeBlockNumber, String safeBlockHash,
            String decision, String canonicalStatus, Long paymentEventId) {
    }

    private record Previous(Long originalId, Long reversalId) {
    }

    private record SettlementSource(long id, long obligationId, long planId, long ledgerTransactionId) {
    }

    private record LedgerLine(long accountId, String direction, BigInteger amount) {
    }
}
