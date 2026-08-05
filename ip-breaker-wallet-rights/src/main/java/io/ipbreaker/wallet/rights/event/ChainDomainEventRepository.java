package io.ipbreaker.wallet.rights.event;

import java.math.BigInteger;
import java.util.List;

public interface ChainDomainEventRepository {
    PersistedEvent save(DecodedContractEvent event);

    List<ChainDomainEvent> findCanonical(
            long networkId, AggregateType type, BigInteger aggregateId);

    List<ChainDomainEvent> findAssetTimeline(
            long networkId, BigInteger assetId, EventCursor after, int limit);

    record PersistedEvent(ChainDomainEvent event, boolean newlyCanonicalized, boolean unknown) {
    }

    record EventCursor(long blockNumber, int transactionIndex, int logIndex) {
    }
}
