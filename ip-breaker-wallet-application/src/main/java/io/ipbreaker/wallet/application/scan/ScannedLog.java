package io.ipbreaker.wallet.application.scan;

import java.util.List;

public record ScannedLog(
        String contractAddress,
        int logIndex,
        List<String> topics,
        String data) {
    public ScannedLog {
        topics = List.copyOf(topics);
    }
}
