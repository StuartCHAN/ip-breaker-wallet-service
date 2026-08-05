package io.ipbreaker.wallet.rights.event;

import io.ipbreaker.wallet.rights.contract.ManagedContract;
import java.math.BigInteger;
import java.util.Map;

public record DecodedContractEvent(
        ManagedContract contract,
        LogEnvelope log,
        String eventName,
        DomainEventType eventType,
        AggregateType aggregateType,
        BigInteger aggregateId,
        BigInteger relatedAssetId,
        Map<String, Object> payload,
        String payloadHash,
        String projectionErrorCode) {
    public DecodedContractEvent {
        payload = Map.copyOf(payload);
    }

    public boolean unknown() {
        return eventType == DomainEventType.UNKNOWN_CONTRACT_EVENT;
    }
}
