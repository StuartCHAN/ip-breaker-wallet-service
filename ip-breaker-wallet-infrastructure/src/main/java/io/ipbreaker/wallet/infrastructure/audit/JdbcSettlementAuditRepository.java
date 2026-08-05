package io.ipbreaker.wallet.infrastructure.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.application.audit.SettlementAssuranceFacts;
import io.ipbreaker.wallet.application.audit.SettlementAssuranceFacts.ReconciliationCheckView;
import io.ipbreaker.wallet.application.audit.SettlementAssuranceFacts.ReconciliationDifferenceView;
import io.ipbreaker.wallet.application.audit.SettlementAuditRepository;
import io.ipbreaker.wallet.application.audit.SettlementAuditTrail;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSettlementAuditRepository implements SettlementAuditRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSettlementAuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SettlementAuditTrail> findTrail(String networkCode, BigInteger agreementId) {
        Optional<AuditRoot> root = root(networkCode, agreementId);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        AuditRoot value = root.get();
        Object[] agreementArgs = {value.networkId(), value.escrowAddress(), agreementId.toString()};
        List<Map<String, Object>> chainStates = rows("""
                SELECT n.network_code, n.chain_id, n.native_symbol,
                       n.required_confirmations, n.status network_status,
                       sc.last_scanned_block, sc.last_scanned_hash,
                       sc.updated_at cursor_updated_at
                FROM chain_network n
                JOIN scan_cursor sc ON sc.network_id = n.id
                WHERE n.id = ?
                """, value.networkId());
        List<Map<String, Object>> contracts = rows("""
                SELECT id contract_id, contract_type, contract_address, abi_version,
                       deployment_block, deployment_tx_hash, runtime_code_hash, status,
                       created_at, updated_at
                FROM chain_contract WHERE network_id = ? ORDER BY contract_type, contract_address
                """, value.networkId());
        List<Map<String, Object>> assets = rows("""
                SELECT p.asset_id, p.owner_address, p.title, p.asset_type, p.jurisdiction,
                       p.document_hash, p.metadata_uri, p.asset_status, p.registered_at,
                       p.version projection_version, p.source_event_id,
                       p.effective_block_number, p.effective_block_hash, p.effective_log_index
                FROM ip_asset_projection p
                JOIN chain_contract contract ON contract.network_id = p.network_id
                  AND contract.contract_address = p.registry_address
                  AND contract.contract_type = 'IP_ASSET_REGISTRY' AND contract.status = 'ACTIVE'
                WHERE p.network_id = ? AND p.asset_id = ?
                ORDER BY p.version DESC LIMIT 1
                """, value.networkId(), value.assetId().toString());
        List<Map<String, Object>> evidence = rows("""
                SELECT evidence.evidence_id, evidence.asset_id, evidence.evidence_type,
                       evidence.evidence_hash, evidence.evidence_uri, evidence.attestation_uid,
                       evidence.submitted_by, evidence.submitted_at, evidence.status,
                       evidence.reviewed_by, evidence.reviewed_at,
                       evidence.version projection_version, evidence.source_event_id,
                       evidence.effective_block_number, evidence.effective_block_hash,
                       evidence.effective_log_index
                FROM evidence_projection evidence
                JOIN chain_contract contract ON contract.network_id = evidence.network_id
                  AND contract.contract_address = evidence.registry_address
                  AND contract.contract_type = 'EVIDENCE_REGISTRY' AND contract.status = 'ACTIVE'
                WHERE evidence.network_id = ? AND evidence.asset_id = ? ORDER BY evidence.evidence_id
                """, value.networkId(), value.assetId().toString());
        List<Map<String, Object>> agreements = rows("""
                SELECT agreement_id, asset_id, escrow_address, licensor, licensee, arbiter,
                       license_fee_raw, escrowed_amount_raw, terms_hash, terms_uri, status,
                       created_at, funded_at, released_to, released_amount_raw,
                       dispute_raised_by, dispute_paid_to_licensor, dispute_resolved_amount_raw,
                       version projection_version, source_event_id, effective_block_number,
                       effective_block_hash, effective_log_index
                FROM license_agreement_projection
                WHERE network_id = ? AND escrow_address = ? AND agreement_id = ?
                """, agreementArgs);
        List<Map<String, Object>> terms = rows("""
                SELECT id terms_manifest_id, terms_version, schema_version, manifest_hash,
                       canonical_json, asset_id, licensor, licensee, payer, payee,
                       currency_kind, amount_raw, status, created_at
                FROM license_terms_manifest
                WHERE network_id = ? AND escrow_address = ? AND agreement_id = ?
                ORDER BY terms_version DESC LIMIT 1
                """, agreementArgs);
        List<Map<String, Object>> obligations = rows("""
                SELECT id obligation_id, agreement_id, terms_manifest_id, asset_id, payer, payee,
                       currency_kind, amount_raw, settlement_status, control_status,
                       matched_payment_event_id, current_snapshot_id, version, created_at, updated_at
                FROM payment_obligation WHERE id = ?
                """, value.obligationId());
        List<Map<String, Object>> payments = value.paymentEventId() == null ? List.of() : rows("""
                SELECT id event_id, event_name, event_type, block_number, block_hash, block_timestamp,
                       tx_hash, transaction_index, log_index, contract_address, topic0,
                       raw_topics_json, raw_data, payload_json, payload_hash, decoder_version,
                       canonical_status
                FROM chain_domain_event WHERE id = ?
                """, value.paymentEventId());
        List<Map<String, Object>> snapshots = rows("""
                SELECT id snapshot_id, safe_block_number, safe_block_hash, asset_state_version,
                       asset_source_event_id, evidence_set_hash, license_state_version,
                       license_source_event_id, license_terms_version,
                       license_terms_manifest_hash, licensor, licensee, payment_event_id,
                       eligibility_decision, decision_reason_codes_json, canonical_status,
                       created_at, orphaned_at
                FROM settlement_eligibility_snapshot
                WHERE obligation_id = ? ORDER BY id
                """, value.obligationId());
        List<Map<String, Object>> plans = rows("""
                SELECT id allocation_plan_id, eligibility_snapshot_id, currency_kind,
                       total_amount_raw, policy_version, plan_hash, created_at
                FROM settlement_allocation_plan WHERE obligation_id = ? ORDER BY id
                """, value.obligationId());
        List<Map<String, Object>> allocationLines = rows("""
                SELECT line.id allocation_line_id, line.allocation_plan_id, line.line_number,
                       line.recipient, line.amount_raw, line.created_at
                FROM settlement_allocation_line line
                JOIN settlement_allocation_plan plan ON plan.id = line.allocation_plan_id
                WHERE plan.obligation_id = ? ORDER BY line.allocation_plan_id, line.line_number
                """, value.obligationId());
        List<Map<String, Object>> settlements = rows("""
                SELECT id settlement_record_id, allocation_plan_id, settlement_status,
                       original_settlement_id, reversed_by_settlement_id,
                       restored_from_reversal_id, ledger_transaction_id, trigger_event_id,
                       safe_block_number, safe_block_hash, created_at
                FROM settlement_record WHERE obligation_id = ? ORDER BY id
                """, value.obligationId());
        List<Map<String, Object>> transactions = rows("""
                SELECT ledger_tx.id ledger_transaction_id, ledger_tx.business_type,
                       ledger_tx.business_id, ledger_tx.reference_no, ledger_tx.status,
                       ledger_tx.description, ledger_tx.created_at
                FROM ledger_transaction ledger_tx
                JOIN settlement_record settlement
                  ON settlement.ledger_transaction_id = ledger_tx.id
                WHERE settlement.obligation_id = ? ORDER BY settlement.id
                """, value.obligationId());
        List<Map<String, Object>> entries = rows("""
                SELECT entry.id ledger_entry_id, entry.ledger_transaction_id,
                       account.id ledger_account_id, account.owner_type, account.owner_id,
                       asset.asset_code, account.account_type, entry.direction,
                       entry.amount_raw, entry.created_at
                FROM ledger_entry entry
                JOIN ledger_account account ON account.id = entry.ledger_account_id
                JOIN asset asset ON asset.id = account.asset_id
                JOIN settlement_record settlement
                  ON settlement.ledger_transaction_id = entry.ledger_transaction_id
                WHERE settlement.obligation_id = ?
                ORDER BY settlement.id, entry.id
                """, value.obligationId());
        List<Map<String, Object>> events = rows("""
                SELECT id event_id, event_name, event_type, aggregate_type, aggregate_id,
                       related_asset_id,
                       block_number, block_hash, block_timestamp, tx_hash, transaction_index,
                       log_index, contract_address, topic0, raw_topics_json, raw_data,
                       payload_json, payload_hash, decoder_version, canonical_status, orphaned_at
                FROM chain_domain_event
                WHERE network_id = ? AND (related_asset_id = ? OR
                    (contract_address = ? AND aggregate_type = 'LICENSE_AGREEMENT'
                     AND aggregate_id = ?))
                ORDER BY block_number, transaction_index, log_index
                """, value.networkId(), value.assetId().toString(), value.escrowAddress(),
                agreementId.toString());
        return Optional.of(new SettlementAuditTrail(
                networkCode, agreementId, first(chainStates), contracts, first(assets), evidence,
                first(agreements), first(terms), first(obligations), first(payments), snapshots,
                plans, allocationLines, settlements, transactions, entries, events));
    }

    @Override
    public Optional<SettlementAssuranceFacts> findAssuranceFacts(
            String networkCode, BigInteger agreementId) {
        Optional<AuditRoot> root = root(networkCode, agreementId);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        AuditRoot value = root.get();
        int activeContracts = count("""
                SELECT COUNT(*) FROM chain_contract
                WHERE network_id = ? AND status = 'ACTIVE'
                """, value.networkId());
        int caughtUpBackfills = count("""
                SELECT COUNT(*) FROM ip_contract_backfill_cursor
                WHERE network_id = ? AND status = 'CAUGHT_UP'
                """, value.networkId());
        int incompleteBackfills = Math.abs(3 - caughtUpBackfills);
        int runningRebuilds = count("""
                SELECT COUNT(*) FROM projection_rebuild_record
                WHERE network_id = ? AND status = 'RUNNING'
                """, value.networkId());
        int orphanedSnapshot = count("""
                SELECT COUNT(*) FROM settlement_eligibility_snapshot snapshot
                JOIN payment_obligation obligation ON obligation.current_snapshot_id = snapshot.id
                WHERE obligation.id = ? AND snapshot.canonical_status = 'ORPHANED'
                """, value.obligationId());
        int unknownEvents = count("""
                SELECT COUNT(*) FROM chain_unknown_event unknown_event
                JOIN chain_domain_event event ON event.id = unknown_event.domain_event_id
                WHERE event.network_id = ? AND event.canonical_status = 'CANONICAL'
                  AND unknown_event.resolved_at IS NULL
                  AND (event.related_asset_id = ? OR
                    (event.contract_address = ? AND event.aggregate_type = 'LICENSE_AGREEMENT'
                     AND event.aggregate_id = ?))
                """, value.networkId(), value.assetId().toString(), value.escrowAddress(),
                agreementId.toString());
        int unbalancedJournals = count("""
                SELECT COUNT(*) FROM (
                    SELECT settlement.id
                    FROM settlement_record settlement
                    LEFT JOIN ledger_entry entry
                      ON entry.ledger_transaction_id = settlement.ledger_transaction_id
                    WHERE settlement.obligation_id = ?
                    GROUP BY settlement.id
                    HAVING COUNT(entry.id) < 2 OR
                      SUM(CASE WHEN entry.direction = 'DEBIT' THEN entry.amount_raw ELSE 0 END) <>
                      SUM(CASE WHEN entry.direction = 'CREDIT' THEN entry.amount_raw ELSE 0 END)
                ) journal_difference
                """, value.obligationId());
        List<ReconciliationCheckView> checks = jdbcTemplate.query("""
                SELECT check_type, difference_count, completed_at
                FROM reconciliation_checkpoint WHERE network_code = ? ORDER BY check_type
                """, (resultSet, rowNumber) -> new ReconciliationCheckView(
                        resultSet.getString("check_type"), resultSet.getInt("difference_count"),
                        resultSet.getTimestamp("completed_at").toInstant()), networkCode);
        Instant lastCompleteCycle = checks.size() == 3 ? checks.stream()
                .map(ReconciliationCheckView::completedAt).min(Instant::compareTo).orElse(null) : null;
        List<ReconciliationDifferenceView> differences = jdbcTemplate.query("""
                SELECT id, check_type, asset_code, subject_type, subject_key,
                       expected_amount_raw, actual_amount_raw, details,
                       occurrence_count, last_detected_at
                FROM reconciliation_difference
                WHERE network_code = ? AND status = 'OPEN'
                ORDER BY last_detected_at DESC, id DESC
                """, (resultSet, rowNumber) -> new ReconciliationDifferenceView(
                        resultSet.getLong("id"), resultSet.getString("check_type"),
                        resultSet.getString("asset_code"), resultSet.getString("subject_type"),
                        resultSet.getString("subject_key"),
                        resultSet.getBigDecimal("expected_amount_raw").toPlainString(),
                        resultSet.getBigDecimal("actual_amount_raw").toPlainString(),
                        resultSet.getString("details"), resultSet.getLong("occurrence_count"),
                        resultSet.getTimestamp("last_detected_at").toInstant()), networkCode);
        return Optional.of(new SettlementAssuranceFacts(
                value.controlStatus(), value.settlementStatus(), activeContracts,
                incompleteBackfills, runningRebuilds, orphanedSnapshot, unknownEvents,
                unbalancedJournals, checks.size(), checks, differences, lastCompleteCycle,
                value.safeBlockNumber(), value.safeBlockHash()));
    }

    @Override
    public StoredProof storeProof(
            String networkCode, BigInteger agreementId, String packageVersion,
            String contentHash, String packageJson) {
        AuditRoot root = root(networkCode, agreementId)
                .orElseThrow(() -> new IllegalStateException("Settlement audit root disappeared"));
        Long settlementId = jdbcTemplate.query("""
                SELECT id FROM settlement_record
                WHERE obligation_id = ? ORDER BY id DESC LIMIT 1
                """, (resultSet, rowNumber) -> resultSet.getLong("id"), root.obligationId())
                .stream().findFirst().orElse(null);
        jdbcTemplate.update("""
                INSERT IGNORE INTO settlement_proof_package
                    (network_id, agreement_id, obligation_id, settlement_record_id,
                     package_version, hash_algorithm, content_hash, package_json)
                VALUES (?, ?, ?, ?, ?, 'SHA-256', ?, CAST(? AS JSON))
                """, root.networkId(), agreementId.toString(), root.obligationId(), settlementId,
                packageVersion, contentHash, packageJson);
        return jdbcTemplate.queryForObject("""
                SELECT id, generated_at FROM settlement_proof_package
                WHERE network_id = ? AND agreement_id = ? AND content_hash = ?
                """, (resultSet, rowNumber) -> new StoredProof(
                        resultSet.getLong("id"), resultSet.getTimestamp("generated_at").toInstant()),
                root.networkId(), agreementId.toString(), contentHash);
    }

    private Optional<AuditRoot> root(String networkCode, BigInteger agreementId) {
        return jdbcTemplate.query("""
                SELECT obligation.id obligation_id, obligation.network_id,
                       obligation.escrow_address, obligation.asset_id,
                       obligation.matched_payment_event_id, obligation.control_status,
                       obligation.settlement_status, snapshot.safe_block_number,
                       snapshot.safe_block_hash
                FROM payment_obligation obligation
                JOIN chain_network network ON network.id = obligation.network_id
                JOIN license_terms_manifest terms ON terms.id = obligation.terms_manifest_id
                JOIN chain_contract managed_escrow
                  ON managed_escrow.network_id = obligation.network_id
                 AND managed_escrow.contract_address = obligation.escrow_address
                 AND managed_escrow.contract_type = 'LICENSE_ESCROW'
                 AND managed_escrow.status = 'ACTIVE'
                LEFT JOIN settlement_eligibility_snapshot snapshot
                  ON snapshot.id = obligation.current_snapshot_id
                WHERE network.network_code = ? AND obligation.agreement_id = ?
                  AND terms.status = 'ACTIVE'
                ORDER BY terms.terms_version DESC LIMIT 1
                """, (resultSet, rowNumber) -> new AuditRoot(
                        resultSet.getLong("obligation_id"), resultSet.getLong("network_id"),
                        resultSet.getString("escrow_address"),
                        integer(resultSet.getBigDecimal("asset_id")),
                        nullableLong(resultSet.getObject("matched_payment_event_id")),
                        resultSet.getString("control_status"),
                        resultSet.getString("settlement_status"),
                        nullableLong(resultSet.getObject("safe_block_number")),
                        resultSet.getString("safe_block_hash")),
                networkCode.toUpperCase(Locale.ROOT), agreementId.toString()).stream().findFirst();
    }

    private List<Map<String, Object>> rows(String sql, Object... arguments) {
        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> source : jdbcTemplate.queryForList(sql, arguments)) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            source.forEach((key, value) -> normalized.put(
                    key.toLowerCase(Locale.ROOT), normalize(key, value)));
            output.add(Collections.unmodifiableMap(normalized));
        }
        return List.copyOf(output);
    }

    private Map<String, Object> first(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private Object normalize(String key, Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof String text && key.toLowerCase(Locale.ROOT).endsWith("_json")) {
            try {
                return objectMapper.readValue(text, Object.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored audit JSON is invalid: " + key, exception);
            }
        }
        return value;
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private BigInteger integer(BigDecimal value) {
        return value.toBigIntegerExact();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private record AuditRoot(
            long obligationId,
            long networkId,
            String escrowAddress,
            BigInteger assetId,
            Long paymentEventId,
            String controlStatus,
            String settlementStatus,
            Long safeBlockNumber,
            String safeBlockHash) {
    }

}
