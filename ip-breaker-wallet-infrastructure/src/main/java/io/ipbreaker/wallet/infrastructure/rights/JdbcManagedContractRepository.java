package io.ipbreaker.wallet.infrastructure.rights;

import io.ipbreaker.wallet.rights.contract.ContractType;
import io.ipbreaker.wallet.rights.contract.ManagedContract;
import io.ipbreaker.wallet.rights.contract.ManagedContractRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcManagedContractRepository implements ManagedContractRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcManagedContractRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ManagedContract> findActive(long networkId) {
        return jdbcTemplate.query(
                "SELECT id, network_id, contract_type, contract_address, abi_version, "
                        + "deployment_block, runtime_code_hash FROM chain_contract "
                        + "WHERE network_id = ? AND status = 'ACTIVE'",
                (resultSet, rowNumber) -> map(resultSet), networkId);
    }

    @Override
    public Optional<ManagedContract> findActive(long networkId, String address) {
        return jdbcTemplate.query(
                "SELECT id, network_id, contract_type, contract_address, abi_version, "
                        + "deployment_block, runtime_code_hash FROM chain_contract WHERE network_id = ? "
                        + "AND contract_address = ? AND status = 'ACTIVE'",
                (resultSet, rowNumber) -> map(resultSet), networkId,
                address.toLowerCase(Locale.ROOT)).stream().findFirst();
    }

    private ManagedContract map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ManagedContract(
                resultSet.getLong("id"), resultSet.getLong("network_id"),
                ContractType.valueOf(resultSet.getString("contract_type")),
                resultSet.getString("contract_address"), resultSet.getString("abi_version"),
                resultSet.getLong("deployment_block"), resultSet.getString("runtime_code_hash"));
    }
}
