package io.ipbreaker.wallet.api.ledger;

import io.ipbreaker.wallet.domain.ledger.LedgerEntry;

public record LedgerEntryResponse(
        long accountId,
        String ownerType,
        String ownerId,
        String accountType,
        String direction,
        String amountRaw) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.accountId(),
                entry.ownerType(),
                entry.ownerId(),
                entry.accountType(),
                entry.direction(),
                entry.amountRaw().toString());
    }
}
