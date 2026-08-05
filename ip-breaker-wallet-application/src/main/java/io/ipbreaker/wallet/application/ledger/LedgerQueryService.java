package io.ipbreaker.wallet.application.ledger;

import io.ipbreaker.wallet.domain.ledger.Balance;
import io.ipbreaker.wallet.domain.ledger.LedgerTransaction;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerQueryService {
    private final LedgerRepository repository;

    public LedgerQueryService(LedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Balance> findBalances(String userId) {
        return repository.findBalances(userId);
    }

    @Transactional(readOnly = true)
    public List<LedgerTransaction> findTransactions(String userId) {
        return repository.findTransactions(userId);
    }
}
