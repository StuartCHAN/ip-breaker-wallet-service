package io.ipbreaker.wallet.application.settlement;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

public record SettlementJournalView(
        long settlementRecordId,
        String status,
        long ledgerTransactionId,
        String reference,
        Instant postedAt,
        List<Entry> entries) {

    public record Entry(String ownerType, String ownerId, String accountType,
            String direction, BigInteger amount) {
    }
}
