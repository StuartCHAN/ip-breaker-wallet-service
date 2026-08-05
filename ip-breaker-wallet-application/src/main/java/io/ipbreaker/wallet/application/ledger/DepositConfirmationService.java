package io.ipbreaker.wallet.application.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositConfirmationService {
    private final LedgerRepository repository;

    public DepositConfirmationService(LedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void update(String networkCode, long latestBlockNumber) {
        repository.updateDepositConfirmations(networkCode, latestBlockNumber);
    }
}
