package io.ipbreaker.wallet.rights.event;

import java.time.Instant;
import java.util.List;

public record LogEnvelope(
        long networkId,
        String blockHash,
        long blockNumber,
        Instant blockTimestamp,
        String transactionHash,
        int transactionIndex,
        String transactionInput,
        String contractAddress,
        int logIndex,
        List<String> topics,
        String data) {
    public LogEnvelope {
        topics = List.copyOf(topics);
    }
}
