package io.ipbreaker.wallet.infrastructure.rights;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.rights.event.AggregateType;
import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository;
import io.ipbreaker.wallet.rights.event.DecodedContractEvent;
import io.ipbreaker.wallet.rights.event.DomainEventType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChainDomainEventRepository implements ChainDomainEventRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcChainDomainEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public PersistedEvent save(DecodedContractEvent decoded) {
        Optional<StoredStatus> existing = findStatus(decoded);
        if (existing.isPresent()) {
            StoredStatus status = existing.orElseThrow();
            if ("CANONICAL".equals(status.canonicalStatus())) {
                return new PersistedEvent(read(status.id()), false, decoded.unknown());
            }
            int changed = jdbcTemplate.update(
                    "UPDATE chain_domain_event SET canonical_status = 'CANONICAL', orphaned_at = NULL, "
                            + "block_number = ?, block_hash = ?, block_timestamp = ?, transaction_index = ? "
                            + "WHERE id = ? AND canonical_status = 'ORPHANED' AND raw_topics_json = ? "
                            + "AND raw_data = ? AND payload_hash = ?",
                    decoded.log().blockNumber(), decoded.log().blockHash(),
                    Timestamp.from(decoded.log().blockTimestamp()), decoded.log().transactionIndex(),
                    status.id(), json(decoded.log().topics()), decoded.log().data(), decoded.payloadHash());
            if (changed != 1) {
                throw new IllegalStateException("Orphaned event content changed on canonical chain");
            }
            return new PersistedEvent(read(status.id()), true, decoded.unknown());
        }
        BigInteger relatedAssetId = resolveRelatedAsset(decoded);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO chain_domain_event (network_id, contract_id, contract_address, "
                            + "block_number, block_hash, block_timestamp, tx_hash, transaction_index, "
                            + "log_index, topic0, raw_topics_json, raw_data, event_name, event_type, "
                            + "aggregate_type, aggregate_id, related_asset_id, payload_json, payload_hash, "
                            + "decoder_version, canonical_status, projection_error_code) VALUES "
                            + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CANONICAL', ?)",
                    Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            statement.setLong(index++, decoded.log().networkId());
            statement.setLong(index++, decoded.contract().id());
            statement.setString(index++, decoded.contract().address());
            statement.setLong(index++, decoded.log().blockNumber());
            statement.setString(index++, decoded.log().blockHash());
            statement.setTimestamp(index++, Timestamp.from(decoded.log().blockTimestamp()));
            statement.setString(index++, decoded.log().transactionHash());
            statement.setInt(index++, decoded.log().transactionIndex());
            statement.setInt(index++, decoded.log().logIndex());
            statement.setString(index++, decoded.log().topics().getFirst().toLowerCase());
            statement.setString(index++, json(decoded.log().topics()));
            statement.setString(index++, decoded.log().data().toLowerCase());
            statement.setString(index++, decoded.eventName());
            statement.setString(index++, decoded.eventType().name());
            statement.setString(index++, decoded.aggregateType() == null ? null : decoded.aggregateType().name());
            statement.setBigDecimal(index++, decimal(decoded.aggregateId()));
            statement.setBigDecimal(index++, decimal(relatedAssetId));
            statement.setString(index++, json(decoded.payload()));
            statement.setString(index++, decoded.payloadHash());
            statement.setString(index++, decoded.contract().abiVersion());
            statement.setString(index, relatedAssetId == null
                    ? decoded.projectionErrorCode() : null);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated key returned for domain event");
        }
        long id = key.longValue();
        if (decoded.unknown()) {
            jdbcTemplate.update("INSERT INTO chain_unknown_event (domain_event_id, reason_code) VALUES (?, ?)",
                    id, "TOPIC_NOT_IN_ABI");
        }
        return new PersistedEvent(read(id), true, decoded.unknown());
    }

    @Override
    public List<ChainDomainEvent> findCanonical(
            long networkId, AggregateType type, BigInteger aggregateId) {
        return jdbcTemplate.query(baseSelect()
                        + " WHERE network_id = ? AND aggregate_type = ? AND aggregate_id = ? "
                        + "AND canonical_status = 'CANONICAL' ORDER BY block_number, transaction_index, log_index",
                (resultSet, rowNumber) -> map(resultSet), networkId, type.name(), aggregateId.toString());
    }

    @Override
    public List<ChainDomainEvent> findAssetTimeline(
            long networkId, BigInteger assetId, EventCursor after, int limit) {
        EventCursor cursor = after == null ? new EventCursor(0, -1, -1) : after;
        return jdbcTemplate.query(baseSelect()
                        + " WHERE network_id = ? AND related_asset_id = ? AND canonical_status = 'CANONICAL' "
                        + "AND (block_number > ? OR (block_number = ? AND transaction_index > ?) "
                        + "OR (block_number = ? AND transaction_index = ? AND log_index > ?)) "
                        + "ORDER BY block_number, transaction_index, log_index LIMIT ?",
                (resultSet, rowNumber) -> map(resultSet), networkId, assetId.toString(),
                cursor.blockNumber(), cursor.blockNumber(), cursor.transactionIndex(),
                cursor.blockNumber(), cursor.transactionIndex(), cursor.logIndex(), limit);
    }

    private Optional<StoredStatus> findStatus(DecodedContractEvent event) {
        return jdbcTemplate.query(
                "SELECT id, canonical_status FROM chain_domain_event WHERE network_id = ? "
                        + "AND contract_address = ? AND tx_hash = ? AND log_index = ?",
                (resultSet, rowNumber) -> new StoredStatus(
                        resultSet.getLong("id"), resultSet.getString("canonical_status")),
                event.log().networkId(), event.contract().address(), event.log().transactionHash(),
                event.log().logIndex()).stream().findFirst();
    }

    private BigInteger resolveRelatedAsset(DecodedContractEvent event) {
        if (event.relatedAssetId() != null || event.aggregateType() == null || event.aggregateId() == null) {
            return event.relatedAssetId();
        }
        return jdbcTemplate.query(
                "SELECT related_asset_id FROM chain_domain_event WHERE network_id = ? "
                        + "AND contract_address = ? AND aggregate_type = ? AND aggregate_id = ? "
                        + "AND canonical_status = 'CANONICAL' AND related_asset_id IS NOT NULL "
                        + "ORDER BY block_number, transaction_index, log_index LIMIT 1",
                (resultSet, rowNumber) -> resultSet.getBigDecimal("related_asset_id").toBigIntegerExact(),
                event.log().networkId(), event.contract().address(), event.aggregateType().name(),
                event.aggregateId().toString()).stream().findFirst().orElse(null);
    }

    private ChainDomainEvent read(long id) {
        return jdbcTemplate.queryForObject(baseSelect() + " WHERE id = ?",
                (resultSet, rowNumber) -> map(resultSet), id);
    }

    private ChainDomainEvent map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        String aggregate = resultSet.getString("aggregate_type");
        BigDecimal aggregateId = resultSet.getBigDecimal("aggregate_id");
        BigDecimal relatedAssetId = resultSet.getBigDecimal("related_asset_id");
        try {
            return new ChainDomainEvent(
                    resultSet.getLong("id"), resultSet.getLong("network_id"),
                    resultSet.getLong("contract_id"), resultSet.getString("contract_address"),
                    resultSet.getLong("block_number"), resultSet.getString("block_hash"),
                    resultSet.getTimestamp("block_timestamp").toInstant(), resultSet.getString("tx_hash"),
                    resultSet.getInt("transaction_index"), resultSet.getInt("log_index"),
                    DomainEventType.valueOf(resultSet.getString("event_type")),
                    aggregate == null ? null : AggregateType.valueOf(aggregate),
                    aggregateId == null ? null : aggregateId.toBigIntegerExact(),
                    relatedAssetId == null ? null : relatedAssetId.toBigIntegerExact(),
                    objectMapper.readValue(resultSet.getString("payload_json"), MAP_TYPE));
        } catch (JsonProcessingException exception) {
            throw new java.sql.SQLException("Invalid domain event payload", exception);
        }
    }

    private String baseSelect() {
        return "SELECT id, network_id, contract_id, contract_address, block_number, block_hash, "
                + "block_timestamp, tx_hash, transaction_index, log_index, event_type, aggregate_type, "
                + "aggregate_id, related_asset_id, payload_json FROM chain_domain_event";
    }

    private BigDecimal decimal(BigInteger value) {
        return value == null ? null : new BigDecimal(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize domain event", exception);
        }
    }

    private record StoredStatus(long id, String canonicalStatus) {
    }
}
