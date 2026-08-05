package io.ipbreaker.wallet.infrastructure.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.application.settlement.EligibilityDecision;
import io.ipbreaker.wallet.application.settlement.EligibilityEvaluator;
import io.ipbreaker.wallet.application.settlement.EligibilityFacts;
import io.ipbreaker.wallet.application.settlement.EligibilityResult;
import io.ipbreaker.wallet.application.settlement.ObligationView;
import io.ipbreaker.wallet.application.settlement.SettlementEligibilityRepository;
import io.ipbreaker.wallet.application.settlement.SettlementStatus;
import io.ipbreaker.wallet.application.settlement.TermsManifest;
import io.ipbreaker.wallet.application.settlement.TermsManifestConflictException;
import io.ipbreaker.wallet.rights.event.AggregateType;
import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import io.ipbreaker.wallet.rights.event.DomainEventType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@Repository
public class JdbcSettlementEligibilityRepository implements SettlementEligibilityRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSettlementEligibilityRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ObligationView registerManifest(
            String networkCode, BigInteger agreementId, TermsManifest manifest,
            String manifestHash, String canonicalJson) {
        Agreement agreement = agreement(networkCode, agreementId).orElseThrow(
                () -> new TermsManifestConflictException("Canonical license agreement is unavailable"));
        validateManifest(agreement, manifest, manifestHash);
        jdbcTemplate.update(
                "INSERT IGNORE INTO license_terms_manifest (network_id, escrow_address, agreement_id, "
                        + "terms_version, schema_version, manifest_hash, canonical_json, asset_id, licensor, "
                        + "licensee, payer, payee, currency_kind, amount_raw, status) VALUES "
                        + "(?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                agreement.networkId(), agreement.escrowAddress(), agreementId.toString(),
                manifest.termsVersion(), manifest.schemaVersion(), manifestHash, canonicalJson,
                manifest.assetId().toString(), lower(manifest.licensor()), lower(manifest.licensee()),
                lower(manifest.payer()), lower(manifest.payee()), manifest.currency(),
                manifest.amount().toString());
        Manifest stored = jdbcTemplate.queryForObject(
                "SELECT id, canonical_json FROM license_terms_manifest WHERE network_id = ? "
                        + "AND escrow_address = ? AND agreement_id = ? AND terms_version = ?",
                (resultSet, rowNumber) -> new Manifest(
                        resultSet.getLong("id"), resultSet.getString("canonical_json")),
                agreement.networkId(), agreement.escrowAddress(), agreementId.toString(),
                manifest.termsVersion());
        if (stored == null || !jsonEqual(stored.canonicalJson(), canonicalJson)) {
            throw new TermsManifestConflictException("Terms version already contains different content");
        }
        jdbcTemplate.update(
                "INSERT IGNORE INTO payment_obligation (network_id, escrow_address, agreement_id, "
                        + "terms_manifest_id, asset_id, payer, payee, currency_kind, amount_raw, "
                        + "settlement_status, control_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'PENDING', 'CLEAR')",
                agreement.networkId(), agreement.escrowAddress(), agreementId.toString(), stored.id(),
                manifest.assetId().toString(), lower(manifest.payer()), lower(manifest.payee()),
                manifest.currency(), manifest.amount().toString());
        ObligationReference obligation = jdbcTemplate.queryForObject(
                "SELECT id, current_snapshot_id FROM payment_obligation WHERE terms_manifest_id = ? "
                        + "FOR UPDATE",
                (resultSet, rowNumber) -> new ObligationReference(
                        resultSet.getLong("id"), nullableLong(resultSet, "current_snapshot_id")),
                stored.id());
        if (obligation == null) {
            throw new IllegalStateException("Payment obligation was not created");
        }
        if (obligation.snapshotId() == null) {
            evaluateObligation(obligation.id(), null, null, null);
        }
        return find(networkCode, agreementId).orElseThrow();
    }

    @Override
    public Optional<ObligationView> find(String networkCode, BigInteger agreementId) {
        return jdbcTemplate.query(
                "SELECT o.id, n.network_code, o.escrow_address, o.agreement_id, t.terms_version, "
                        + "t.manifest_hash, o.asset_id, o.payer, o.payee, o.currency_kind, o.amount_raw, "
                        + "o.settlement_status, o.control_status, o.matched_payment_event_id, "
                        + "o.current_snapshot_id, s.eligibility_decision, s.decision_reason_codes_json, "
                        + "s.safe_block_number, s.safe_block_hash, o.updated_at FROM payment_obligation o "
                        + "JOIN chain_network n ON n.id = o.network_id JOIN license_terms_manifest t "
                        + "ON t.id = o.terms_manifest_id LEFT JOIN settlement_eligibility_snapshot s "
                        + "ON s.id = o.current_snapshot_id WHERE n.network_code = ? AND o.agreement_id = ? "
                        + "AND t.status = 'ACTIVE' ORDER BY t.terms_version DESC LIMIT 1",
                (resultSet, rowNumber) -> new ObligationView(
                        resultSet.getLong("id"), resultSet.getString("network_code"),
                        resultSet.getString("escrow_address"), number(resultSet.getBigDecimal("agreement_id")),
                        resultSet.getLong("terms_version"), resultSet.getString("manifest_hash"),
                        number(resultSet.getBigDecimal("asset_id")), resultSet.getString("payer"),
                        resultSet.getString("payee"), resultSet.getString("currency_kind"),
                        number(resultSet.getBigDecimal("amount_raw")),
                        resultSet.getString("settlement_status"), resultSet.getString("control_status"),
                        nullableLong(resultSet, "matched_payment_event_id"),
                        nullableLong(resultSet, "current_snapshot_id"),
                        resultSet.getString("eligibility_decision"),
                        reasons(resultSet.getString("decision_reason_codes_json")),
                        nullableLong(resultSet, "safe_block_number"), resultSet.getString("safe_block_hash"),
                        resultSet.getTimestamp("updated_at").toInstant()),
                networkCode, agreementId.toString()).stream().findFirst();
    }

    @Override
    public void evaluate(ChainDomainEvent event) {
        if (event.aggregateType() == AggregateType.LICENSE_AGREEMENT) {
            for (Long id : obligationIds(event.networkId(), "agreement_id", event.aggregateId())) {
                updateControlStatus(id, event.eventType());
                evaluateObligation(id, event.eventType() == DomainEventType.LICENSE_FUNDED ? event : null,
                        event.blockNumber(), event.blockHash());
            }
        }
    }

    @Override
    public void rollbackAfter(long networkId, long ancestorBlock, String ancestorBlockHash) {
        jdbcTemplate.update(
                "UPDATE payment_obligation_match m JOIN chain_domain_event e ON e.id = m.payment_event_id "
                        + "SET m.canonical_status = 'ORPHANED', m.orphaned_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE e.network_id = ? AND e.block_number > ? AND m.canonical_status = 'CANONICAL'",
                networkId, ancestorBlock);
        jdbcTemplate.update(
                "UPDATE settlement_eligibility_snapshot SET canonical_status = 'ORPHANED', "
                        + "orphaned_at = CURRENT_TIMESTAMP(6) WHERE obligation_id IN "
                        + "(SELECT id FROM payment_obligation WHERE network_id = ?) "
                        + "AND safe_block_number > ? AND canonical_status = 'CANONICAL'",
                networkId, ancestorBlock);
        jdbcTemplate.update(
                "UPDATE payment_obligation o JOIN license_agreement_projection a "
                        + "ON a.network_id = o.network_id AND a.escrow_address = o.escrow_address "
                        + "AND a.agreement_id = o.agreement_id SET o.control_status = CASE "
                        + "WHEN o.control_status = 'HELD' THEN 'HELD' WHEN a.status = 'DISPUTED' "
                        + "THEN 'DISPUTED' ELSE 'CLEAR' END WHERE o.network_id = ?",
                networkId);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT o.id FROM payment_obligation o JOIN settlement_eligibility_snapshot s "
                        + "ON s.id = o.current_snapshot_id WHERE o.network_id = ? "
                        + "AND s.canonical_status = 'ORPHANED'",
                Long.class, networkId);
        for (Long id : ids) {
            evaluateObligation(id, null, ancestorBlock, ancestorBlockHash);
        }
    }

    private void evaluateObligation(Long obligationId, ChainDomainEvent triggeringPayment,
            Long safeBlockOverride, String safeBlockHashOverride) {
        EvaluationInput input = loadInput(obligationId);
        Payment payment = payment(input, triggeringPayment);
        EligibilityResult result = EligibilityEvaluator.evaluate(new EligibilityFacts(
                input.assetOwner(), input.assetStatus(), input.licensor(), input.licensee(),
                input.manifestHash(), input.chainTermsHash(), input.agreementStatus(), input.assetId(),
                input.agreementAssetId(), input.payer(), input.payee(), input.currency(), input.amount(),
                input.licenseFee(), payment == null ? null : payment.payer(),
                payment == null ? null : payment.amount()));
        EligibilityDecision decision = result.decision();
        List<String> reasons = result.reasonCodes().stream().map(Enum::name).toList();
        long snapshotId = insertSnapshot(input, payment, decision, reasons,
                safeBlockOverride, safeBlockHashOverride);
        jdbcTemplate.update(
                "UPDATE payment_obligation SET settlement_status = CASE "
                        + "WHEN settlement_status IN ('PENDING', 'ELIGIBLE') THEN ? "
                        + "ELSE settlement_status END, matched_payment_event_id = ?, "
                        + "current_snapshot_id = ?, version = version + 1 WHERE id = ?",
                decision == EligibilityDecision.ELIGIBLE
                        ? SettlementStatus.ELIGIBLE.name() : SettlementStatus.PENDING.name(),
                payment == null ? null : payment.eventId(), snapshotId, obligationId);
        if (payment != null) {
            jdbcTemplate.update(
                    "INSERT INTO payment_obligation_match (obligation_id, payment_event_id, match_status, "
                            + "reason_codes_json, canonical_status) VALUES (?, ?, ?, CAST(? AS JSON), "
                            + "'CANONICAL') ON DUPLICATE KEY UPDATE match_status = VALUES(match_status), "
                            + "reason_codes_json = VALUES(reason_codes_json), canonical_status = 'CANONICAL', "
                            + "orphaned_at = NULL",
                    obligationId, payment.eventId(), reasons.isEmpty() ? "MATCHED" : "REJECTED", json(reasons));
        }
    }

    private EvaluationInput loadInput(long obligationId) {
        return jdbcTemplate.queryForObject(
                "SELECT o.id, o.network_id, o.escrow_address, o.agreement_id, o.asset_id, o.payer, "
                        + "o.payee, o.currency_kind, o.amount_raw, t.terms_version, t.manifest_hash, "
                        + "a.licensor, a.licensee, a.asset_id agreement_asset_id, a.license_fee_raw, "
                        + "a.terms_hash, a.status agreement_status, a.version license_version, "
                        + "a.source_event_id license_event_id, "
                        + "p.owner_address, p.asset_status, p.version asset_version, "
                        + "p.source_event_id asset_event_id, c.last_scanned_block, c.last_scanned_hash "
                        + "FROM payment_obligation o JOIN license_terms_manifest t ON t.id = o.terms_manifest_id "
                        + "JOIN license_agreement_projection a ON a.network_id = o.network_id "
                        + "AND a.escrow_address = o.escrow_address AND a.agreement_id = o.agreement_id "
                        + "JOIN scan_cursor c ON c.network_id = o.network_id LEFT JOIN ip_asset_projection p "
                        + "ON p.network_id = o.network_id AND p.asset_id = o.asset_id "
                        + "AND p.registry_address = (SELECT contract_address FROM chain_contract "
                        + "WHERE network_id = o.network_id AND contract_type = 'IP_ASSET_REGISTRY' "
                        + "AND status = 'ACTIVE' LIMIT 1) WHERE o.id = ? FOR UPDATE",
                (resultSet, rowNumber) -> new EvaluationInput(
                        resultSet.getLong("id"), resultSet.getLong("network_id"),
                        resultSet.getString("escrow_address"), number(resultSet.getBigDecimal("agreement_id")),
                        number(resultSet.getBigDecimal("asset_id")), resultSet.getString("payer"),
                        resultSet.getString("payee"), resultSet.getString("currency_kind"),
                        number(resultSet.getBigDecimal("amount_raw")), resultSet.getLong("terms_version"),
                        resultSet.getString("manifest_hash"), resultSet.getString("licensor"),
                        resultSet.getString("licensee"), number(resultSet.getBigDecimal("agreement_asset_id")),
                        number(resultSet.getBigDecimal("license_fee_raw")), resultSet.getString("terms_hash"),
                        resultSet.getString("agreement_status"),
                        resultSet.getLong("license_version"), resultSet.getLong("license_event_id"),
                        resultSet.getString("owner_address"), resultSet.getString("asset_status"),
                        nullableLong(resultSet, "asset_version"), nullableLong(resultSet, "asset_event_id"),
                        resultSet.getLong("last_scanned_block"), resultSet.getString("last_scanned_hash")),
                obligationId);
    }

    private Payment payment(EvaluationInput input, ChainDomainEvent triggering) {
        if (triggering != null && input.agreementId().equals(triggering.aggregateId())) {
            return new Payment(triggering.id(), lower(String.valueOf(triggering.payload().get("fundedBy"))),
                    new BigInteger(String.valueOf(triggering.payload().get("amount"))));
        }
        return jdbcTemplate.query(
                "SELECT id, payload_json FROM chain_domain_event WHERE network_id = ? "
                        + "AND contract_address = ? AND aggregate_type = 'LICENSE_AGREEMENT' "
                        + "AND aggregate_id = ? AND event_type = 'LICENSE_FUNDED' "
                        + "AND canonical_status = 'CANONICAL' ORDER BY block_number DESC, log_index DESC LIMIT 1",
                (resultSet, rowNumber) -> {
                    Map<?, ?> payload = readMap(resultSet.getString("payload_json"));
                    return new Payment(resultSet.getLong("id"), lower(String.valueOf(payload.get("fundedBy"))),
                            new BigInteger(String.valueOf(payload.get("amount"))));
                }, input.networkId(), input.escrowAddress(), input.agreementId().toString())
                .stream().findFirst().orElse(null);
    }

    private long insertSnapshot(
            EvaluationInput input, Payment payment, EligibilityDecision decision, List<String> reasons,
            Long safeBlockOverride, String safeBlockHashOverride) {
        String evidenceHash = evidenceSetHash(input.networkId(), input.assetId());
        jdbcTemplate.update(
                "INSERT INTO settlement_eligibility_snapshot (obligation_id, safe_block_number, "
                        + "safe_block_hash, asset_state_version, asset_source_event_id, evidence_set_hash, "
                        + "license_state_version, license_source_event_id, license_terms_version, "
                        + "license_terms_manifest_hash, licensor, licensee, payment_event_id, "
                        + "eligibility_decision, decision_reason_codes_json, canonical_status) VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), 'CANONICAL')",
                input.obligationId(), safeBlockOverride == null ? input.safeBlock() : safeBlockOverride,
                safeBlockHashOverride == null ? input.safeBlockHash() : safeBlockHashOverride,
                input.assetVersion(),
                input.assetEventId(), evidenceHash, input.licenseVersion(), input.licenseEventId(),
                input.termsVersion(), input.manifestHash(), input.licensor(), input.licensee(),
                payment == null ? null : payment.eventId(), decision.name(), json(reasons));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0 : id;
    }

    private String evidenceSetHash(long networkId, BigInteger assetId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT evidence_id, evidence_hash, status, version FROM evidence_projection "
                        + "WHERE network_id = ? AND asset_id = ? ORDER BY evidence_id",
                (resultSet, rowNumber) -> resultSet.getString("evidence_id") + ":"
                        + resultSet.getString("evidence_hash") + ":" + resultSet.getString("status")
                        + ":" + resultSet.getLong("version"), networkId, assetId.toString());
        return Numeric.toHexString(Hash.sha3(String.join("|", rows).getBytes(StandardCharsets.UTF_8)))
                .toLowerCase(Locale.ROOT);
    }

    private List<Long> obligationIds(long networkId, String field, BigInteger value) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM payment_obligation WHERE network_id = ? AND " + field + " = ?",
                Long.class, networkId, value.toString());
    }

    private void updateControlStatus(long obligationId, DomainEventType eventType) {
        if (eventType == DomainEventType.LICENSE_DISPUTE_RAISED) {
            jdbcTemplate.update(
                    "UPDATE payment_obligation SET control_status = 'DISPUTED', version = version + 1 "
                            + "WHERE id = ? AND control_status <> 'DISPUTED'",
                    obligationId);
        } else if (eventType == DomainEventType.LICENSE_DISPUTE_RESOLVED) {
            jdbcTemplate.update(
                    "UPDATE payment_obligation SET control_status = 'CLEAR', version = version + 1 "
                            + "WHERE id = ? AND control_status = 'DISPUTED'",
                    obligationId);
        }
    }

    private Optional<Agreement> agreement(String networkCode, BigInteger agreementId) {
        return jdbcTemplate.query(
                "SELECT p.network_id, p.escrow_address, p.asset_id, p.licensor, p.licensee, "
                        + "p.license_fee_raw, p.terms_hash FROM license_agreement_projection p "
                        + "JOIN chain_network n ON n.id = p.network_id JOIN chain_contract c "
                        + "ON c.network_id = p.network_id AND c.contract_address = p.escrow_address "
                        + "AND c.contract_type = 'LICENSE_ESCROW' AND c.status = 'ACTIVE' "
                        + "WHERE n.network_code = ? AND p.agreement_id = ?",
                (resultSet, rowNumber) -> new Agreement(
                        resultSet.getLong("network_id"), resultSet.getString("escrow_address"),
                        number(resultSet.getBigDecimal("asset_id")), resultSet.getString("licensor"),
                        resultSet.getString("licensee"), number(resultSet.getBigDecimal("license_fee_raw")),
                        resultSet.getString("terms_hash")), networkCode, agreementId.toString())
                .stream().findFirst();
    }

    private void validateManifest(Agreement agreement, TermsManifest manifest, String hash) {
        if (!agreement.assetId().equals(manifest.assetId())
                || !agreement.licensor().equals(lower(manifest.licensor()))
                || !agreement.licensee().equals(lower(manifest.licensee()))
                || !agreement.licenseFee().equals(manifest.amount())
                || !agreement.termsHash().equals(hash)) {
            throw new TermsManifestConflictException(
                    "Structured terms do not match the canonical on-chain agreement");
        }
    }

    private List<String> reasons(String json) throws java.sql.SQLException {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new java.sql.SQLException("Invalid eligibility reason codes", exception);
        }
    }

    private Map<?, ?> readMap(String json) throws java.sql.SQLException {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new java.sql.SQLException("Invalid payment event payload", exception);
        }
    }

    private boolean jsonEqual(String left, String right) {
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored terms JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write eligibility JSON", exception);
        }
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private BigInteger number(BigDecimal value) {
        return value == null ? null : value.toBigIntegerExact();
    }

    private String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private record Manifest(long id, String canonicalJson) { }

    private record ObligationReference(long id, Long snapshotId) { }

    private record Agreement(long networkId, String escrowAddress, BigInteger assetId,
            String licensor, String licensee, BigInteger licenseFee, String termsHash) { }

    private record Payment(long eventId, String payer, BigInteger amount) { }

    private record EvaluationInput(
            long obligationId, long networkId, String escrowAddress, BigInteger agreementId,
            BigInteger assetId, String payer, String payee, String currency, BigInteger amount,
            long termsVersion, String manifestHash, String licensor, String licensee,
            BigInteger agreementAssetId, BigInteger licenseFee, String chainTermsHash,
            String agreementStatus, long licenseVersion, long licenseEventId,
            String assetOwner, String assetStatus,
            Long assetVersion, Long assetEventId, long safeBlock, String safeBlockHash) { }
}
