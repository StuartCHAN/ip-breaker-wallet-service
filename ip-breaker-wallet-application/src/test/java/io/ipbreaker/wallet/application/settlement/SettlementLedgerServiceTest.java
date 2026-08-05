package io.ipbreaker.wallet.application.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementLedgerServiceTest {
    @Test
    void postReturnsRepositorySettlement() {
        SettlementView expected = view();
        SettlementLedgerService service = new SettlementLedgerService(new FakeRepository(expected));

        assertEquals(expected, service.post("SEPOLIA", BigInteger.ONE));
    }

    @Test
    void postRejectsIneligibleOrControlledObligation() {
        SettlementLedgerService service = new SettlementLedgerService(new FakeRepository(null));

        assertThrows(SettlementNotPostableException.class,
                () -> service.post("SEPOLIA", BigInteger.ONE));
    }

    private SettlementView view() {
        return new SettlementView(
                1, 2, "SEPOLIA", BigInteger.ONE, "SETTLED", "CLEAR", 3,
                "PAYEE_100_V1", "0x" + "1".repeat(64), BigInteger.TEN,
                List.of(new SettlementView.AllocationView(1,
                        "0x1111111111111111111111111111111111111111", BigInteger.TEN)),
                null, null, null, 4, 100, "0x" + "2".repeat(64), Instant.EPOCH);
    }

    private static final class FakeRepository implements SettlementLedgerRepository {
        private final SettlementView settlement;

        private FakeRepository(SettlementView settlement) {
            this.settlement = settlement;
        }

        @Override
        public Optional<SettlementView> postEligible(String networkCode, BigInteger agreementId) {
            return Optional.ofNullable(settlement);
        }

        @Override
        public void settleOrRestore(ChainDomainEvent trigger) {
        }

        @Override
        public void reverseOrphaned(long networkId, long ancestorBlock, String ancestorBlockHash) {
        }

        @Override
        public Optional<SettlementView> find(String networkCode, BigInteger agreementId) {
            return Optional.ofNullable(settlement);
        }

        @Override
        public List<SettlementJournalView> journals(long settlementId) {
            return List.of();
        }
    }
}
