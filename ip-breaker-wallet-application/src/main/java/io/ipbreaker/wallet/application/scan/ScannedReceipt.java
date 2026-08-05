package io.ipbreaker.wallet.application.scan;

import java.math.BigInteger;
import java.util.List;

public record ScannedReceipt(
        String transactionHash,
        long blockNumber,
        int transactionIndex,
        boolean success,
        BigInteger gasUsed,
        String contractAddress,
        List<ScannedLog> logs) {
    public ScannedReceipt {
        logs = List.copyOf(logs);
    }
}
