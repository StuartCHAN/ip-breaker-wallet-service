package io.ipbreaker.wallet.application.address;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ipbreaker.wallet.domain.address.AddressType;
import io.ipbreaker.wallet.domain.address.DepositAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DepositAddressServiceTest {
    @Test
    void concurrentRequestsReturnOneBindingForSameUser() throws InterruptedException {
        InMemoryRepository repository = new InMemoryRepository();
        DepositAddressAllocator allocator = new DepositAddressAllocator(repository);
        DepositAddressService service = new DepositAddressService(repository, allocator);
        Set<String> results = ConcurrentHashMap.newKeySet();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 32; index++) {
                executor.submit(() -> results.add(
                        service.getOrAllocate("SEPOLIA", "user-1001").address()));
            }
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertEquals(Set.of("0x1111111111111111111111111111111111111111"), results);
        assertEquals(1, repository.assignmentCount());
    }

    private static final class InMemoryRepository implements DepositAddressRepository {
        private DepositAddress assigned;
        private int assignmentCount;

        @Override
        public synchronized Optional<DepositAddress> findAssigned(
                String networkCode, String userId, AddressType addressType) {
            return Optional.ofNullable(assigned);
        }

        @Override
        public synchronized Optional<DepositAddress> assignAvailable(
                String networkCode, String userId, AddressType addressType) {
            if (assigned == null) {
                assigned = new DepositAddress(
                        1L,
                        1L,
                        networkCode,
                        userId,
                        "0x1111111111111111111111111111111111111111",
                        addressType,
                        Instant.now());
                assignmentCount++;
            }
            return Optional.of(assigned);
        }

        synchronized int assignmentCount() {
            return assignmentCount;
        }
    }
}
