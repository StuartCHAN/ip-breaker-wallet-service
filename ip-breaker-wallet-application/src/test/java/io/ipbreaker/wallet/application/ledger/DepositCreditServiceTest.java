package io.ipbreaker.wallet.application.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ipbreaker.wallet.domain.ledger.Balance;
import io.ipbreaker.wallet.domain.ledger.LedgerTransaction;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DepositCreditServiceTest {
    @Test
    void repeatedTaskCreditsTheDepositOnlyOnce() {
        FakeLedgerRepository repository = new FakeLedgerRepository();
        DepositCreditService service = new DepositCreditService(repository);

        assertTrue(service.credit(7L));
        assertFalse(service.credit(7L));

        assertEquals(1, repository.transactions);
        assertEquals(2, repository.entries.size());
        assertEquals(2, repository.balanceUpdates);
        assertEquals(List.of("DEBIT", "CREDIT"), repository.entries);
    }

    private static final class FakeLedgerRepository implements LedgerRepository {
        private boolean credited;

        private int transactions;

        private int balanceUpdates;

        private final List<String> entries = new ArrayList<>();

        @Override
        public void updateDepositConfirmations(String networkCode, long latestBlockNumber) {
        }

        @Override
        public List<Long> findConfirmedDepositIds(String networkCode, int limit) {
            return credited ? List.of() : List.of(7L);
        }

        @Override
        public Optional<ConfirmedDeposit> lockConfirmedDeposit(long depositId) {
            if (credited) {
                return Optional.empty();
            }
            return Optional.of(new ConfirmedDeposit(
                    depositId, 3L, "SEPOLIA", "user-1", BigInteger.valueOf(1000)));
        }

        @Override
        public long getOrCreatePlatformAssetAccount(ConfirmedDeposit deposit) {
            return 11L;
        }

        @Override
        public long getOrCreateUserLiabilityAccount(ConfirmedDeposit deposit) {
            return 12L;
        }

        @Override
        public long createDepositLedgerTransaction(long depositId) {
            transactions++;
            return 21L;
        }

        @Override
        public void insertEntry(
                long transactionId, long accountId, String direction, String amountRaw) {
            entries.add(direction);
        }

        @Override
        public void increaseAvailableBalance(long accountId, String amountRaw) {
            balanceUpdates++;
        }

        @Override
        public boolean markDepositCredited(long depositId, long transactionId) {
            credited = true;
            return true;
        }

        @Override
        public List<Balance> findBalances(String userId) {
            return List.of();
        }

        @Override
        public List<LedgerTransaction> findTransactions(String userId) {
            return List.of();
        }
    }
}
