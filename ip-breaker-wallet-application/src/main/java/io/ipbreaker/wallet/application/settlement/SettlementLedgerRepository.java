package io.ipbreaker.wallet.application.settlement;

import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public interface SettlementLedgerRepository {
    Optional<SettlementView> postEligible(String networkCode, BigInteger agreementId);

    void settleOrRestore(ChainDomainEvent trigger);

    void reverseOrphaned(long networkId, long ancestorBlock, String ancestorBlockHash);

    Optional<SettlementView> find(String networkCode, BigInteger agreementId);

    List<SettlementJournalView> journals(long settlementId);
}
