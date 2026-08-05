package io.ipbreaker.wallet.domain.ledger;

import java.time.Instant;
import java.util.List;

public record LedgerTransaction(
        long id,
        String businessType,
        long businessId,
        String referenceNo,
        String status,
        String assetCode,
        Instant createdAt,
        List<LedgerEntry> entries) {
    public LedgerTransaction {
        entries = List.copyOf(entries);
    }
}
