package io.ipbreaker.wallet.api.ledger;

import io.ipbreaker.wallet.domain.ledger.Balance;
import java.time.Instant;

public record BalanceResponse(
        String userId,
        String networkCode,
        String assetCode,
        String symbol,
        int decimals,
        String availableAmountRaw,
        String pendingAmountRaw,
        long version,
        Instant updatedAt) {
    public static BalanceResponse from(Balance balance) {
        return new BalanceResponse(
                balance.userId(),
                balance.networkCode(),
                balance.assetCode(),
                balance.symbol(),
                balance.decimals(),
                balance.availableAmountRaw().toString(),
                balance.pendingAmountRaw().toString(),
                balance.version(),
                balance.updatedAt());
    }
}
