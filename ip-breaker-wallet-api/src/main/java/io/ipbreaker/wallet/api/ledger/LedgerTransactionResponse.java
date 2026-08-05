package io.ipbreaker.wallet.api.ledger;

import io.ipbreaker.wallet.domain.ledger.LedgerTransaction;
import java.time.Instant;
import java.util.List;

public record LedgerTransactionResponse(
        long id,
        String businessType,
        long businessId,
        String referenceNo,
        String status,
        String assetCode,
        Instant createdAt,
        List<LedgerEntryResponse> entries) {
    public static LedgerTransactionResponse from(LedgerTransaction transaction) {
        return new LedgerTransactionResponse(
                transaction.id(),
                transaction.businessType(),
                transaction.businessId(),
                transaction.referenceNo(),
                transaction.status(),
                transaction.assetCode(),
                transaction.createdAt(),
                transaction.entries().stream().map(LedgerEntryResponse::from).toList());
    }
}
