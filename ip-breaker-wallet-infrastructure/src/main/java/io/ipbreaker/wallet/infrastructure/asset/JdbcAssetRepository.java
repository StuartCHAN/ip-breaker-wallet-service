package io.ipbreaker.wallet.infrastructure.asset;

import io.ipbreaker.wallet.application.asset.AssetRepository;
import io.ipbreaker.wallet.domain.asset.Asset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAssetRepository implements AssetRepository {
    private static final String SELECT_ENABLED = """
            SELECT a.id, n.network_code, a.asset_code, a.asset_type, a.contract_address,
                   a.symbol, a.decimals, a.deposit_enabled
            FROM asset a
            JOIN chain_network n ON n.id = a.network_id
            WHERE a.deposit_enabled = TRUE AND n.status = 'ACTIVE'
            ORDER BY n.chain_id, a.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAssetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Asset> findDepositEnabled() {
        return jdbcTemplate.query(SELECT_ENABLED, (resultSet, rowNumber) -> new Asset(
                resultSet.getLong("id"),
                resultSet.getString("network_code"),
                resultSet.getString("asset_code"),
                resultSet.getString("asset_type"),
                resultSet.getString("contract_address"),
                resultSet.getString("symbol"),
                resultSet.getInt("decimals"),
                resultSet.getBoolean("deposit_enabled")));
    }
}
