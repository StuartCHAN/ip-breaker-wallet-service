package io.ipbreaker.wallet.infrastructure.rights;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.rights.event.AggregateType;
import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository;
import io.ipbreaker.wallet.rights.event.DomainEventType;
import io.ipbreaker.wallet.rights.projection.ProjectionInvariantException;
import io.ipbreaker.wallet.rights.projection.RightsProjectionRepository;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRightsProjectionRepository implements RightsProjectionRepository {
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final JdbcTemplate jdbcTemplate;
    private final ChainDomainEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public JdbcRightsProjectionRepository(
            JdbcTemplate jdbcTemplate,
            ChainDomainEventRepository eventRepository,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void apply(ChainDomainEvent event) {
        boolean changed = switch (event.eventType()) {
            case IP_ASSET_REGISTERED -> registerAsset(event);
            case IP_ASSET_TRANSFERRED -> transferAsset(event);
            case EVIDENCE_ADDED -> addEvidence(event);
            case EVIDENCE_STATUS_CHANGED -> changeEvidenceStatus(event);
            case LICENSE_AGREEMENT_CREATED -> createAgreement(event);
            case LICENSE_STATUS_CHANGED -> changeAgreementStatus(event);
            case LICENSE_FUNDED -> fundAgreement(event);
            case LICENSE_FUNDS_RELEASED -> releaseAgreement(event);
            case LICENSE_DISPUTE_RAISED -> raiseDispute(event);
            case LICENSE_DISPUTE_RESOLVED -> resolveDispute(event);
            default -> false;
        };
        if (changed) {
            recordHistory(event);
        }
    }

    @Override
    public void rollbackAndRebuild(long networkId, long ancestorBlock) {
        List<AggregateKey> affected = jdbcTemplate.query(
                "SELECT DISTINCT aggregate_type, aggregate_id FROM chain_domain_event "
                        + "WHERE network_id = ? AND block_number > ? AND canonical_status = 'CANONICAL' "
                        + "AND aggregate_type IN ('IP_ASSET','EVIDENCE','LICENSE_AGREEMENT')",
                (resultSet, rowNumber) -> new AggregateKey(
                        AggregateType.valueOf(resultSet.getString("aggregate_type")),
                        resultSet.getBigDecimal("aggregate_id").toBigIntegerExact()),
                networkId, ancestorBlock);
        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chain_domain_event WHERE network_id = ? AND block_number > ? "
                        + "AND canonical_status = 'CANONICAL'",
                Long.class, networkId, ancestorBlock);
        if (eventCount == null || eventCount == 0) {
            return;
        }
        long toBlock = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(block_number), ?) FROM chain_domain_event "
                        + "WHERE network_id = ? AND canonical_status = 'CANONICAL'",
                Long.class, ancestorBlock, networkId);
        jdbcTemplate.update(
                "INSERT INTO projection_rebuild_record (network_id, reason, ancestor_block_number, "
                        + "from_block_number, to_block_number, status) VALUES (?, 'REORG', ?, ?, ?, 'RUNNING')",
                networkId, ancestorBlock, ancestorBlock + 1, toBlock);
        Long rebuildId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                "UPDATE chain_domain_event SET canonical_status = 'ORPHANED', "
                        + "orphaned_at = CURRENT_TIMESTAMP(6) WHERE network_id = ? AND block_number > ? "
                        + "AND canonical_status = 'CANONICAL'",
                networkId, ancestorBlock);
        jdbcTemplate.update(
                "UPDATE projection_history h JOIN chain_domain_event e ON e.id = h.valid_from_event_id "
                        + "SET h.canonical_status = 'ORPHANED', h.invalidated_by_rebuild_id = ? "
                        + "WHERE e.network_id = ? AND e.block_number > ? AND h.canonical_status = 'CANONICAL'",
                rebuildId, networkId, ancestorBlock);
        Set<AggregateKey> unique = new LinkedHashSet<>(affected);
        for (AggregateKey key : unique) {
            deleteCurrent(networkId, key);
            for (ChainDomainEvent event : eventRepository.findCanonical(networkId, key.type(), key.id())) {
                apply(event);
            }
        }
        jdbcTemplate.update(
                "UPDATE projection_rebuild_record SET status = 'COMPLETED', affected_event_count = ?, "
                        + "completed_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                eventCount, rebuildId);
    }

    private boolean registerAsset(ChainDomainEvent event) {
        Map<String, Object> payload = event.payload();
        int changed = jdbcTemplate.update(
                "INSERT INTO ip_asset_projection (network_id, registry_address, asset_id, owner_address, "
                        + "title, asset_type, jurisdiction, document_hash, metadata_uri, asset_status, "
                        + "registered_at, version, source_event_id, effective_block_number, "
                        + "effective_block_hash, effective_log_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'ACTIVE', ?, 1, ?, ?, ?, ?)",
                event.networkId(), event.contractAddress(), event.aggregateId().toString(),
                text(payload, "owner"), text(payload, "title"), text(payload, "assetType"),
                text(payload, "jurisdiction"), text(payload, "documentHash"), text(payload, "metadataURI"),
                Timestamp.from(event.blockTimestamp()), event.id(), event.blockNumber(), event.blockHash(),
                event.logIndex());
        return changed == 1;
    }

    private boolean transferAsset(ChainDomainEvent event) {
        Map<String, Object> payload = event.payload();
        String to = text(payload, "to");
        List<CurrentOwner> current = jdbcTemplate.query(
                "SELECT owner_address, asset_status FROM ip_asset_projection WHERE network_id = ? "
                        + "AND registry_address = ? AND asset_id = ? FOR UPDATE",
                (resultSet, rowNumber) -> new CurrentOwner(
                        resultSet.getString("owner_address"), resultSet.getString("asset_status")),
                event.networkId(), event.contractAddress(), event.aggregateId().toString());
        if (current.isEmpty()) {
            throw new ProjectionInvariantException("Asset transfer has no registration origin");
        }
        String status = ZERO_ADDRESS.equals(to) ? "BURNED" : "ACTIVE";
        if (current.getFirst().owner().equals(to) && current.getFirst().status().equals(status)) {
            return false;
        }
        int changed = jdbcTemplate.update(
                "UPDATE ip_asset_projection SET owner_address = ?, asset_status = ?, version = version + 1, "
                        + "source_event_id = ?, effective_block_number = ?, effective_block_hash = ?, "
                        + "effective_log_index = ? WHERE network_id = ? AND registry_address = ? "
                        + "AND asset_id = ?",
                to, status, event.id(), event.blockNumber(), event.blockHash(), event.logIndex(),
                event.networkId(), event.contractAddress(), event.aggregateId().toString());
        requireOne(changed, "asset transfer");
        return true;
    }

    private boolean addEvidence(ChainDomainEvent event) {
        Map<String, Object> payload = event.payload();
        int changed = jdbcTemplate.update(
                "INSERT INTO evidence_projection (network_id, registry_address, evidence_id, asset_id, "
                        + "evidence_type, evidence_hash, evidence_uri, attestation_uid, submitted_by, "
                        + "submitted_at, status, version, source_event_id, effective_block_number, "
                        + "effective_block_hash, effective_log_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'SUBMITTED', 1, ?, ?, ?, ?)",
                event.networkId(), event.contractAddress(), event.aggregateId().toString(),
                text(payload, "assetId"), text(payload, "evidenceType"), text(payload, "evidenceHash"),
                text(payload, "evidenceURI"), text(payload, "attestationUID"), text(payload, "submittedBy"),
                Timestamp.from(event.blockTimestamp()), event.id(), event.blockNumber(), event.blockHash(),
                event.logIndex());
        return changed == 1;
    }

    private boolean changeEvidenceStatus(ChainDomainEvent event) {
        Map<String, Object> payload = event.payload();
        int changed = jdbcTemplate.update(
                "UPDATE evidence_projection SET status = ?, reviewed_by = ?, reviewed_at = ?, "
                        + "version = version + 1, source_event_id = ?, effective_block_number = ?, "
                        + "effective_block_hash = ?, effective_log_index = ? WHERE network_id = ? "
                        + "AND registry_address = ? AND evidence_id = ? AND status = ?",
                text(payload, "newStatus"), text(payload, "reviewedBy"), Timestamp.from(event.blockTimestamp()),
                event.id(), event.blockNumber(), event.blockHash(), event.logIndex(), event.networkId(),
                event.contractAddress(), event.aggregateId().toString(), text(payload, "previousStatus"));
        requireOne(changed, "evidence status transition");
        return true;
    }

    private boolean createAgreement(ChainDomainEvent event) {
        Map<String, Object> payload = event.payload();
        int changed = jdbcTemplate.update(
                "INSERT INTO license_agreement_projection (network_id, escrow_address, agreement_id, "
                        + "asset_id, licensor, licensee, arbiter, license_fee_raw, escrowed_amount_raw, "
                        + "terms_hash, terms_uri, status, created_at, version, source_event_id, "
                        + "effective_block_number, effective_block_hash, effective_log_index) VALUES "
                        + "(?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 'CREATED', ?, 1, ?, ?, ?, ?)",
                event.networkId(), event.contractAddress(), event.aggregateId().toString(),
                text(payload, "assetId"), text(payload, "licensor"), text(payload, "licensee"),
                text(payload, "arbiter"), text(payload, "licenseFee"), text(payload, "termsHash"),
                text(payload, "termsURI"), Timestamp.from(event.blockTimestamp()), event.id(),
                event.blockNumber(), event.blockHash(), event.logIndex());
        return changed == 1;
    }

    private boolean changeAgreementStatus(ChainDomainEvent event) {
        Map<String, Object> payload = event.payload();
        int changed = jdbcTemplate.update(
                "UPDATE license_agreement_projection SET status = ?, version = version + 1, "
                        + "source_event_id = ?, effective_block_number = ?, effective_block_hash = ?, "
                        + "effective_log_index = ? WHERE network_id = ? AND escrow_address = ? "
                        + "AND agreement_id = ? AND status = ?",
                text(payload, "newStatus"), event.id(), event.blockNumber(), event.blockHash(),
                event.logIndex(), event.networkId(), event.contractAddress(), event.aggregateId().toString(),
                text(payload, "previousStatus"));
        requireOne(changed, "agreement status transition");
        return true;
    }

    private boolean fundAgreement(ChainDomainEvent event) {
        return updateAgreementFact(event,
                "escrowed_amount_raw = ?, funded_at = ?", text(event.payload(), "amount"),
                Timestamp.from(event.blockTimestamp()));
    }

    private boolean releaseAgreement(ChainDomainEvent event) {
        return updateAgreementFact(event,
                "escrowed_amount_raw = 0, released_to = ?, released_amount_raw = ?",
                text(event.payload(), "to"), text(event.payload(), "amount"));
    }

    private boolean raiseDispute(ChainDomainEvent event) {
        return updateAgreementFact(event, "dispute_raised_by = ?", text(event.payload(), "raisedBy"));
    }

    private boolean resolveDispute(ChainDomainEvent event) {
        return updateAgreementFact(event,
                "escrowed_amount_raw = 0, dispute_paid_to_licensor = ?, "
                        + "dispute_resolved_amount_raw = ?",
                event.payload().get("paidToLicensor"), text(event.payload(), "amount"));
    }

    private boolean updateAgreementFact(ChainDomainEvent event, String setClause, Object... values) {
        String sql = "UPDATE license_agreement_projection SET " + setClause
                + ", version = version + 1, source_event_id = ?, effective_block_number = ?, "
                + "effective_block_hash = ?, effective_log_index = ? WHERE network_id = ? "
                + "AND escrow_address = ? AND agreement_id = ?";
        Object[] arguments = new Object[values.length + 7];
        System.arraycopy(values, 0, arguments, 0, values.length);
        int index = values.length;
        arguments[index++] = event.id();
        arguments[index++] = event.blockNumber();
        arguments[index++] = event.blockHash();
        arguments[index++] = event.logIndex();
        arguments[index++] = event.networkId();
        arguments[index++] = event.contractAddress();
        arguments[index] = event.aggregateId().toString();
        int changed = jdbcTemplate.update(sql, arguments);
        requireOne(changed, "agreement fact update");
        return true;
    }

    private void recordHistory(ChainDomainEvent event) {
        String table = switch (event.aggregateType()) {
            case IP_ASSET -> "ip_asset_projection";
            case EVIDENCE -> "evidence_projection";
            case LICENSE_AGREEMENT -> "license_agreement_projection";
            default -> throw new ProjectionInvariantException("Unsupported projection history type");
        };
        String addressColumn = event.aggregateType() == AggregateType.LICENSE_AGREEMENT
                ? "escrow_address" : "registry_address";
        String idColumn = switch (event.aggregateType()) {
            case IP_ASSET -> "asset_id";
            case EVIDENCE -> "evidence_id";
            case LICENSE_AGREEMENT -> "agreement_id";
            default -> throw new ProjectionInvariantException("Unsupported projection id type");
        };
        Map<String, Object> snapshot = jdbcTemplate.queryForMap(
                "SELECT * FROM " + table + " WHERE network_id = ? AND " + addressColumn
                        + " = ? AND " + idColumn + " = ?",
                event.networkId(), event.contractAddress(), event.aggregateId().toString());
        Object version = snapshot.get("version");
        jdbcTemplate.update(
                "INSERT INTO projection_history (network_id, projection_type, contract_address, "
                        + "aggregate_id, projection_version, snapshot_json, valid_from_event_id, "
                        + "canonical_status) VALUES (?, ?, ?, ?, ?, ?, ?, 'CANONICAL')",
                event.networkId(), event.aggregateType().name(), event.contractAddress(),
                event.aggregateId().toString(), version, json(snapshot), event.id());
    }

    private void deleteCurrent(long networkId, AggregateKey key) {
        String table = switch (key.type()) {
            case IP_ASSET -> "ip_asset_projection";
            case EVIDENCE -> "evidence_projection";
            case LICENSE_AGREEMENT -> "license_agreement_projection";
            default -> throw new ProjectionInvariantException("Unsupported rebuild type");
        };
        String idColumn = switch (key.type()) {
            case IP_ASSET -> "asset_id";
            case EVIDENCE -> "evidence_id";
            case LICENSE_AGREEMENT -> "agreement_id";
            default -> throw new ProjectionInvariantException("Unsupported rebuild id");
        };
        jdbcTemplate.update("DELETE FROM " + table + " WHERE network_id = ? AND " + idColumn + " = ?",
                networkId, key.id().toString());
        jdbcTemplate.update(
                "DELETE FROM projection_history WHERE network_id = ? AND projection_type = ? "
                        + "AND aggregate_id = ? AND canonical_status = 'CANONICAL'",
                networkId, key.type().name(), key.id().toString());
    }

    private void requireOne(int changed, String operation) {
        if (changed != 1) {
            throw new ProjectionInvariantException("Invalid " + operation);
        }
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new ProjectionInvariantException("Missing payload field " + key);
        }
        return value.toString();
    }

    private String json(Map<String, Object> snapshot) {
        Map<String, Object> normalized = new java.util.TreeMap<>();
        snapshot.forEach((key, value) -> normalized.put(key,
                value instanceof java.util.Date date ? date.toInstant().toString() : value));
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize projection history", exception);
        }
    }

    private record AggregateKey(AggregateType type, BigInteger id) {
    }

    private record CurrentOwner(String owner, String status) {
    }
}
