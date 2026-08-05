package io.ipbreaker.wallet.application.settlement;

import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.util.Optional;

public interface SettlementEligibilityRepository {
    ObligationView registerManifest(
            String networkCode, BigInteger agreementId, TermsManifest manifest,
            String manifestHash, String canonicalJson);

    Optional<ObligationView> find(String networkCode, BigInteger agreementId);

    void evaluate(ChainDomainEvent event);

    void rollbackAfter(long networkId, long ancestorBlock, String ancestorBlockHash);
}
