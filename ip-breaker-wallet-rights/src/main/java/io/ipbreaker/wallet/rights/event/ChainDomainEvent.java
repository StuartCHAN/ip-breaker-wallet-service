package io.ipbreaker.wallet.rights.event;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;

public record ChainDomainEvent(
        long id,
        long networkId,
        long contractId,
        String contractAddress,
        long blockNumber,
        String blockHash,
        Instant blockTimestamp,
        String transactionHash,
        int transactionIndex,
        int logIndex,
        DomainEventType eventType,
        AggregateType aggregateType,
        BigInteger aggregateId,
        BigInteger relatedAssetId,
        Map<String, Object> payload) {
}
