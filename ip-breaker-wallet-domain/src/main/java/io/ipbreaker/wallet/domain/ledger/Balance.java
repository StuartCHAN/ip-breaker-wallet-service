package io.ipbreaker.wallet.domain.ledger;

import java.math.BigInteger;
import java.time.Instant;

public record Balance(
        String userId,
        String networkCode,
        String assetCode,
        String symbol,
        int decimals,
        BigInteger availableAmountRaw,
        BigInteger pendingAmountRaw,
        long version,
        Instant updatedAt) {
}
