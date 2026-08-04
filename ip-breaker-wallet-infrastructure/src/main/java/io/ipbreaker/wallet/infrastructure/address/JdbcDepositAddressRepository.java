package io.ipbreaker.wallet.infrastructure.address;

import io.ipbreaker.wallet.application.address.ConcurrentAddressAssignmentException;
import io.ipbreaker.wallet.application.address.DepositAddressRepository;
import io.ipbreaker.wallet.domain.address.AddressType;
import io.ipbreaker.wallet.domain.address.DepositAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDepositAddressRepository implements DepositAddressRepository {
    private static final String SELECT_ASSIGNED = """
            SELECT wa.id, wa.network_id, cn.network_code, wa.user_id, wa.address,
                   wa.address_type, wa.assigned_at
            FROM wallet_address wa
            JOIN chain_network cn ON cn.id = wa.network_id
            WHERE cn.network_code = ? AND wa.user_id = ? AND wa.address_type = ?
            """;

    private static final String SELECT_AVAILABLE_FOR_UPDATE = """
            SELECT wa.id
            FROM wallet_address wa
            JOIN chain_network cn ON cn.id = wa.network_id
            WHERE cn.network_code = ? AND cn.status = 'ACTIVE'
              AND wa.address_type = ? AND wa.status = 'AVAILABLE' AND wa.user_id IS NULL
            ORDER BY wa.id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """;

    private static final String ASSIGN = """
            UPDATE wallet_address
            SET user_id = ?, status = 'ASSIGNED', assigned_at = CURRENT_TIMESTAMP(6)
            WHERE id = ? AND status = 'AVAILABLE' AND user_id IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDepositAddressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DepositAddress> findAssigned(
            String networkCode, String userId, AddressType addressType) {
        List<DepositAddress> matches = jdbcTemplate.query(
                SELECT_ASSIGNED, this::map, networkCode, userId, addressType.name());
        return matches.stream().findFirst();
    }

    @Override
    public Optional<DepositAddress> assignAvailable(
            String networkCode, String userId, AddressType addressType) {
        List<Long> candidates = jdbcTemplate.query(
                SELECT_AVAILABLE_FOR_UPDATE,
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                networkCode,
                addressType.name());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        try {
            int changed = jdbcTemplate.update(ASSIGN, userId, candidates.getFirst());
            if (changed != 1) {
                return Optional.empty();
            }
        } catch (DuplicateKeyException exception) {
            throw new ConcurrentAddressAssignmentException(exception);
        }
        return findAssigned(networkCode, userId, addressType);
    }

    private DepositAddress map(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp assignedAt = resultSet.getTimestamp("assigned_at");
        return new DepositAddress(
                resultSet.getLong("id"),
                resultSet.getLong("network_id"),
                resultSet.getString("network_code"),
                resultSet.getString("user_id"),
                resultSet.getString("address"),
                AddressType.valueOf(resultSet.getString("address_type")),
                assignedAt == null ? null : assignedAt.toInstant());
    }
}
