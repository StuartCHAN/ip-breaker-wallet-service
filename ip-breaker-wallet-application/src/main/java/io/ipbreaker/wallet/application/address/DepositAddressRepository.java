package io.ipbreaker.wallet.application.address;

import io.ipbreaker.wallet.domain.address.AddressType;
import io.ipbreaker.wallet.domain.address.DepositAddress;
import java.util.Optional;

public interface DepositAddressRepository {
    Optional<DepositAddress> findAssigned(String networkCode, String userId, AddressType addressType);

    Optional<DepositAddress> assignAvailable(String networkCode, String userId, AddressType addressType);
}
