package io.ipbreaker.wallet.domain.address;

import java.time.Instant;

public record DepositAddress(
        long id,
        long networkId,
        String networkCode,
        String userId,
        String address,
        AddressType addressType,
        Instant assignedAt) {
}
