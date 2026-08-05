package io.ipbreaker.wallet.application.scan;

import java.time.Instant;
import java.util.List;

public record ScannedBlock(
        long number,
        String hash,
        String parentHash,
        Instant timestamp,
        List<ScannedTransaction> transactions) {

    public ScannedBlock {
        transactions = List.copyOf(transactions);
    }
}
