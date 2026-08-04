package io.ipbreaker.wallet.domain.asset;

public record Asset(
        long id,
        String networkCode,
        String assetCode,
        String assetType,
        String contractAddress,
        String symbol,
        int decimals,
        boolean depositEnabled) {
}
