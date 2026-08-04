package io.ipbreaker.wallet.api.address;

import io.ipbreaker.wallet.domain.address.DepositAddress;
import java.time.Instant;
import org.web3j.crypto.Keys;

public record DepositAddressResponse(
        String networkCode,
        String userId,
        String address,
        String addressType,
        Instant assignedAt) {
    static DepositAddressResponse from(DepositAddress address) {
        return new DepositAddressResponse(
                address.networkCode(),
                address.userId(),
                Keys.toChecksumAddress(address.address()),
                address.addressType().name(),
                address.assignedAt());
    }
}
