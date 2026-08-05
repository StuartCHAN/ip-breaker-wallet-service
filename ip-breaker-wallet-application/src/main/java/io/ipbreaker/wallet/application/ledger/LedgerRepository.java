package io.ipbreaker.wallet.application.ledger;

import io.ipbreaker.wallet.domain.ledger.Balance;
import io.ipbreaker.wallet.domain.ledger.LedgerTransaction;
import java.util.List;
import java.util.Optional;

public interface LedgerRepository {
    void updateDepositConfirmations(String networkCode, long latestBlockNumber);

    List<Long> findConfirmedDepositIds(String networkCode, int limit);

    Optional<ConfirmedDeposit> lockConfirmedDeposit(long depositId);

    long getOrCreatePlatformAssetAccount(ConfirmedDeposit deposit);

    long getOrCreateUserLiabilityAccount(ConfirmedDeposit deposit);

    long createDepositLedgerTransaction(long depositId);

    void insertEntry(long transactionId, long accountId, String direction, String amountRaw);

    void increaseAvailableBalance(long accountId, String amountRaw);

    boolean markDepositCredited(long depositId, long transactionId);

    List<Balance> findBalances(String userId);

    List<LedgerTransaction> findTransactions(String userId);
}
