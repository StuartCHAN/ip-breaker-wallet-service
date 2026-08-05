package io.ipbreaker.wallet.domain.ledger;

import java.math.BigInteger;

public record LedgerEntry(
        long accountId,
        String ownerType,
        String ownerId,
        String accountType,
        String direction,
        BigInteger amountRaw) {
}
