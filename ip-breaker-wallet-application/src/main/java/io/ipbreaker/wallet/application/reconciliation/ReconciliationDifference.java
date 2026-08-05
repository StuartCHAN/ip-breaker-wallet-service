package io.ipbreaker.wallet.application.reconciliation;

import java.math.BigInteger;

public record ReconciliationDifference(
        String type,
        String networkCode,
        String assetCode,
        String subjectType,
        String subjectKey,
        BigInteger expectedAmount,
        BigInteger actualAmount,
        String details) {
}
