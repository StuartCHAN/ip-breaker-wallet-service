package io.ipbreaker.wallet.application.deposit;

import java.math.BigInteger;

public record DepositCandidate(
        String assetType,
        String contractAddress,
        String transactionHash,
        int logIndex,
        String fromAddress,
        String toAddress,
        BigInteger amountRaw,
        long blockNumber) {
}
