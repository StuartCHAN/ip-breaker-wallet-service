package io.ipbreaker.wallet.application.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoubleEntryPostingTest {

    @Test
    void depositReversalFlipsDirectionsAndRemainsBalanced() {
        DoubleEntryPosting posting = DoubleEntryPosting.depositReversal(
                10L, 20L, BigInteger.valueOf(500L));

        assertEquals("CREDIT", posting.entries().get(0).direction());
        assertEquals("DEBIT", posting.entries().get(1).direction());
        assertEquals(posting.entries().get(0).amountRaw(), posting.entries().get(1).amountRaw());
    }

    @Test
    void createsBalancedDepositPosting() {
        DoubleEntryPosting posting = DoubleEntryPosting.deposit(10L, 20L, BigInteger.valueOf(500));

        assertEquals(List.of("DEBIT", "CREDIT"),
                posting.entries().stream().map(DoubleEntryPosting.PostingEntry::direction).toList());
        assertEquals(List.of(BigInteger.valueOf(500), BigInteger.valueOf(500)),
                posting.entries().stream().map(DoubleEntryPosting.PostingEntry::amountRaw).toList());
    }

    @Test
    void rejectsUnbalancedPosting() {
        List<DoubleEntryPosting.PostingEntry> entries = List.of(
                new DoubleEntryPosting.PostingEntry(10L, "DEBIT", BigInteger.valueOf(500)),
                new DoubleEntryPosting.PostingEntry(20L, "CREDIT", BigInteger.valueOf(499)));

        assertThrows(IllegalArgumentException.class, () -> new DoubleEntryPosting(entries));
    }
}
