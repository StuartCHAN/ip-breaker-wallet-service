package io.ipbreaker.wallet.application.ledger;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositCreditService {
    private final LedgerRepository repository;

    public DepositCreditService(LedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean credit(long depositId) {
        Optional<ConfirmedDeposit> locked = repository.lockConfirmedDeposit(depositId);
        if (locked.isEmpty()) {
            return false;
        }
        ConfirmedDeposit deposit = locked.orElseThrow();
        long platformAccountId = repository.getOrCreatePlatformAssetAccount(deposit);
        long userAccountId = repository.getOrCreateUserLiabilityAccount(deposit);
        DoubleEntryPosting posting = DoubleEntryPosting.deposit(
                platformAccountId, userAccountId, deposit.amountRaw());
        long transactionId = repository.createDepositLedgerTransaction(deposit.id());
        for (DoubleEntryPosting.PostingEntry entry : posting.entries()) {
            repository.insertEntry(
                    transactionId,
                    entry.accountId(),
                    entry.direction(),
                    entry.amountRaw().toString());
            repository.increaseAvailableBalance(entry.accountId(), entry.amountRaw().toString());
        }
        if (!repository.markDepositCredited(deposit.id(), transactionId)) {
            throw new IllegalStateException("Locked deposit could not be marked credited");
        }
        return true;
    }
}
