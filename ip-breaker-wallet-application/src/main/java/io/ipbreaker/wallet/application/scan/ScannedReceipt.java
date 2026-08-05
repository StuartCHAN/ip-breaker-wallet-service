package io.ipbreaker.wallet.application.scan;

import java.math.BigInteger;

public record ScannedReceipt(
        String transactionHash,
        long blockNumber,
        int transactionIndex,
        boolean success,
        BigInteger gasUsed,
        String contractAddress) {
}
