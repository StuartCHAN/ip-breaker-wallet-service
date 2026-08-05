package io.ipbreaker.wallet.application.settlement;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

public record SettlementView(
        long settlementId,
        long obligationId,
        String network,
        BigInteger agreementId,
        String status,
        String controlStatus,
        long allocationPlanId,
        String policyVersion,
        String planHash,
        BigInteger totalAmount,
        List<AllocationView> allocations,
        Long originalSettlementId,
        Long reversalSettlementId,
        Long restoredFromReversalId,
        long ledgerTransactionId,
        long safeBlockNumber,
        String safeBlockHash,
        Instant createdAt) {

    public record AllocationView(int lineNumber, String recipient, BigInteger amount) {
    }
}
