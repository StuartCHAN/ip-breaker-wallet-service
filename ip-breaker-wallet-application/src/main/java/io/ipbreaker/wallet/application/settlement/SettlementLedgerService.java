package io.ipbreaker.wallet.application.settlement;

import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementLedgerService {
    private final SettlementLedgerRepository repository;

    public SettlementLedgerService(SettlementLedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SettlementView post(String networkCode, BigInteger agreementId) {
        return repository.postEligible(networkCode, agreementId)
                .orElseThrow(() -> new SettlementNotPostableException(
                        "Obligation is not eligible and clear for posting"));
    }

    @Transactional
    public void postIfEligible(String networkCode, BigInteger agreementId) {
        repository.postEligible(networkCode, agreementId);
    }

    @Transactional
    public void onCanonicalEvent(ChainDomainEvent event) {
        if (event.aggregateType() == io.ipbreaker.wallet.rights.event.AggregateType.LICENSE_AGREEMENT) {
            repository.settleOrRestore(event);
        }
    }

    @Transactional
    public void rollbackAfter(long networkId, long ancestorBlock, String ancestorBlockHash) {
        repository.reverseOrphaned(networkId, ancestorBlock, ancestorBlockHash);
    }

    public SettlementView find(String networkCode, BigInteger agreementId) {
        return repository.find(networkCode, agreementId).orElseThrow(SettlementRecordNotFoundException::new);
    }

    public List<SettlementJournalView> journals(long settlementId) {
        return repository.journals(settlementId);
    }
}
