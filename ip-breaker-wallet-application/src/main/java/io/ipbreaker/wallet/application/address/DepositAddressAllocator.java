package io.ipbreaker.wallet.application.address;

import io.ipbreaker.wallet.domain.address.AddressType;
import io.ipbreaker.wallet.domain.address.DepositAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositAddressAllocator {
    private final DepositAddressRepository repository;

    public DepositAddressAllocator(DepositAddressRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DepositAddress allocate(String networkCode, String userId) {
        return repository.assignAvailable(networkCode, userId, AddressType.DEPOSIT)
                .orElseThrow(() -> new AddressPoolExhaustedException(networkCode));
    }
}
