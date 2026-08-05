package io.ipbreaker.wallet.domain.deposit;

import java.math.BigInteger;
import java.time.Instant;

public record Deposit(
        long id,
        String networkCode,
        String assetCode,
        String assetType,
        String symbol,
        int decimals,
        String userId,
        String transactionHash,
        int logIndex,
        String fromAddress,
        String toAddress,
        BigInteger amountRaw,
        long blockNumber,
        int confirmations,
        String status,
        Instant detectedAt) {
}
