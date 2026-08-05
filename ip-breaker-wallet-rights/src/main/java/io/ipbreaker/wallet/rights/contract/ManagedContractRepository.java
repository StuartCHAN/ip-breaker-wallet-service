package io.ipbreaker.wallet.rights.contract;

import java.util.List;
import java.util.Optional;

public interface ManagedContractRepository {
    List<ManagedContract> findActive(long networkId);

    Optional<ManagedContract> findActive(long networkId, String address);
}
