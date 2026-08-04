package io.ipbreaker.wallet.application.address;

import io.ipbreaker.wallet.domain.address.AddressType;
import io.ipbreaker.wallet.domain.address.DepositAddress;
import org.springframework.stereotype.Service;

@Service
public class DepositAddressService {
    private final DepositAddressRepository repository;

    private final DepositAddressAllocator allocator;

    public DepositAddressService(DepositAddressRepository repository, DepositAddressAllocator allocator) {
        this.repository = repository;
        this.allocator = allocator;
    }

    public DepositAddress get(String networkCode, String userId) {
        return repository.findAssigned(networkCode, userId, AddressType.DEPOSIT)
                .orElseThrow(() -> new DepositAddressNotFoundException(networkCode, userId));
    }

    public DepositAddress getOrAllocate(String networkCode, String userId) {
        return repository.findAssigned(networkCode, userId, AddressType.DEPOSIT)
                .orElseGet(() -> allocateOrReadConcurrentWinner(networkCode, userId));
    }

    private DepositAddress allocateOrReadConcurrentWinner(String networkCode, String userId) {
        try {
            return allocator.allocate(networkCode, userId);
        } catch (ConcurrentAddressAssignmentException exception) {
            return repository.findAssigned(networkCode, userId, AddressType.DEPOSIT)
                    .orElseThrow(() -> exception);
        }
    }
}
