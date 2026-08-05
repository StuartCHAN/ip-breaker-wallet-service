package io.ipbreaker.wallet.application.reconciliation;

public record OnChainBalanceTarget(
        String networkCode,
        String assetCode,
        String assetType,
        String contractAddress,
        String address,
        long blockNumber) {
}
