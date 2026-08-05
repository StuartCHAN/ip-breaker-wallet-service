package io.ipbreaker.wallet.infrastructure.rights;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository.EventCursor;
import io.ipbreaker.wallet.rights.query.RightsQueryRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRightsQueryRepository implements RightsQueryRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRightsQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public IndexState indexState(String networkCode) {
        Integer networks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chain_network WHERE network_code = ? AND status = 'ACTIVE'",
                Integer.class, networkCode);
        if (networks == null || networks == 0) {
            return IndexState.INVALID_NETWORK;
        }
        Integer rebuilding = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM projection_rebuild_record r JOIN chain_network n "
                        + "ON n.id = r.network_id WHERE n.network_code = ? AND r.status = 'RUNNING'",
                Integer.class, networkCode);
        if (rebuilding != null && rebuilding > 0) {
            return IndexState.REBUILDING;
        }
        Integer activeContracts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chain_contract c JOIN chain_network n ON n.id = c.network_id "
                        + "WHERE n.network_code = ? AND c.status = 'ACTIVE'",
                Integer.class, networkCode);
        Integer caughtUpBackfills = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ip_contract_backfill_cursor b JOIN chain_network n "
                        + "ON n.id = b.network_id WHERE n.network_code = ? AND b.status = 'CAUGHT_UP'",
                Integer.class, networkCode);
        return activeContracts != null && activeContracts == 3
                && caughtUpBackfills != null && caughtUpBackfills == 3
                ? IndexState.READY : IndexState.NOT_READY;
    }

    @Override
    public Optional<IpAssetView> findAsset(String networkCode, BigInteger assetId) {
        return jdbcTemplate.query(
                "SELECT n.network_code, p.*, c.last_scanned_block, c.last_scanned_hash "
                        + "FROM ip_asset_projection p JOIN chain_network n ON n.id = p.network_id "
                        + "JOIN scan_cursor c ON c.network_id = p.network_id "
                        + "WHERE n.network_code = ? AND p.asset_id = ?",
                (resultSet, rowNumber) -> new IpAssetView(
                        resultSet.getString("network_code"), resultSet.getString("registry_address"),
                        resultSet.getBigDecimal("asset_id").toBigIntegerExact(),
                        resultSet.getString("owner_address"), resultSet.getString("title"),
                        resultSet.getString("asset_type"), resultSet.getString("jurisdiction"),
                        resultSet.getString("document_hash"), resultSet.getString("metadata_uri"),
                        resultSet.getString("asset_status"), resultSet.getTimestamp("registered_at").toInstant(),
                        resultSet.getLong("version"), resultSet.getLong("last_scanned_block"),
                        resultSet.getString("last_scanned_hash"),
                        evidenceSummary(resultSet.getLong("network_id"), assetId),
                        agreementSummary(resultSet.getLong("network_id"), assetId)),
                networkCode, assetId.toString()).stream().findFirst();
    }

    @Override
    public Optional<LicenseAgreementView> findAgreement(String networkCode, BigInteger agreementId) {
        return jdbcTemplate.query(
                "SELECT n.network_code, p.*, c.last_scanned_block, c.last_scanned_hash "
                        + "FROM license_agreement_projection p JOIN chain_network n ON n.id = p.network_id "
                        + "JOIN scan_cursor c ON c.network_id = p.network_id "
                        + "WHERE n.network_code = ? AND p.agreement_id = ?",
                (resultSet, rowNumber) -> new LicenseAgreementView(
                        resultSet.getString("network_code"), resultSet.getString("escrow_address"),
                        resultSet.getBigDecimal("agreement_id").toBigIntegerExact(),
                        resultSet.getBigDecimal("asset_id").toBigIntegerExact(),
                        resultSet.getString("licensor"), resultSet.getString("licensee"),
                        resultSet.getString("arbiter"),
                        resultSet.getBigDecimal("license_fee_raw").toBigIntegerExact(),
                        resultSet.getBigDecimal("escrowed_amount_raw").toBigIntegerExact(),
                        resultSet.getString("terms_hash"), resultSet.getString("terms_uri"),
                        resultSet.getString("status"), resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("funded_at") == null ? null
                                : resultSet.getTimestamp("funded_at").toInstant(),
                        resultSet.getString("released_to"), number(resultSet.getBigDecimal("released_amount_raw")),
                        resultSet.getLong("version"), resultSet.getLong("last_scanned_block"),
                        resultSet.getString("last_scanned_hash")),
                networkCode, agreementId.toString()).stream().findFirst();
    }

    @Override
    public List<TimelineEventView> findTimeline(
            String networkCode, BigInteger assetId, EventCursor after, int limit) {
        EventCursor cursor = after == null ? new EventCursor(0, -1, -1) : after;
        return jdbcTemplate.query(
                "SELECT e.* FROM chain_domain_event e JOIN chain_network n ON n.id = e.network_id "
                        + "WHERE n.network_code = ? AND e.related_asset_id = ? "
                        + "AND e.canonical_status = 'CANONICAL' AND (e.block_number > ? "
                        + "OR (e.block_number = ? AND e.transaction_index > ?) "
                        + "OR (e.block_number = ? AND e.transaction_index = ? AND e.log_index > ?)) "
                        + "ORDER BY e.block_number, e.transaction_index, e.log_index LIMIT ?",
                (resultSet, rowNumber) -> new TimelineEventView(
                        resultSet.getLong("id"), resultSet.getString("event_type"),
                        resultSet.getString("aggregate_type"), number(resultSet.getBigDecimal("aggregate_id")),
                        resultSet.getLong("block_number"), resultSet.getString("block_hash"),
                        resultSet.getTimestamp("block_timestamp").toInstant(), resultSet.getString("tx_hash"),
                        resultSet.getInt("transaction_index"), resultSet.getInt("log_index"),
                        resultSet.getString("contract_address"), payload(resultSet.getString("payload_json")),
                        resultSet.getString("canonical_status")),
                networkCode, assetId.toString(), cursor.blockNumber(), cursor.blockNumber(),
                cursor.transactionIndex(), cursor.blockNumber(), cursor.transactionIndex(),
                cursor.logIndex(), limit);
    }

    private Map<String, Long> evidenceSummary(long networkId, BigInteger assetId) {
        return summary("SELECT status, COUNT(*) amount FROM evidence_projection WHERE network_id = ? "
                + "AND asset_id = ? GROUP BY status", networkId, assetId,
                List.of("submitted", "verified", "rejected", "revoked"));
    }

    private Map<String, Long> agreementSummary(long networkId, BigInteger assetId) {
        return summary("SELECT status, COUNT(*) amount FROM license_agreement_projection "
                + "WHERE network_id = ? AND asset_id = ? GROUP BY status", networkId, assetId,
                List.of("created", "funded", "active", "disputed", "completed", "refunded", "cancelled"));
    }

    private Map<String, Long> summary(
            String sql, long networkId, BigInteger assetId, List<String> statuses) {
        Map<String, Long> result = new LinkedHashMap<>();
        statuses.forEach(status -> result.put(status, 0L));
        jdbcTemplate.query(sql, resultSet -> {
            result.put(resultSet.getString("status").toLowerCase(), resultSet.getLong("amount"));
        }, networkId, assetId.toString());
        return result;
    }

    private Map<String, Object> payload(String json) throws java.sql.SQLException {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new java.sql.SQLException("Invalid timeline payload", exception);
        }
    }

    private BigInteger number(BigDecimal value) {
        return value == null ? null : value.toBigIntegerExact();
    }
}
