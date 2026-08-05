package io.ipbreaker.wallet.application.ledger;

import java.math.BigInteger;

public record ConfirmedDeposit(
        long id,
        long assetId,
        String networkCode,
        String userId,
        BigInteger amountRaw) {
}
