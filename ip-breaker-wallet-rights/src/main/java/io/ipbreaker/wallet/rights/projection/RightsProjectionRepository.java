package io.ipbreaker.wallet.rights.projection;

import io.ipbreaker.wallet.rights.event.ChainDomainEvent;

public interface RightsProjectionRepository {
    void apply(ChainDomainEvent event);

    void rollbackAndRebuild(long networkId, long ancestorBlock);
}
