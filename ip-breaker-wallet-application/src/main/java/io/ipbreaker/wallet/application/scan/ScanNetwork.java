package io.ipbreaker.wallet.application.scan;

public record ScanNetwork(long id, String code, int requiredConfirmations, long startBlock) {
}
