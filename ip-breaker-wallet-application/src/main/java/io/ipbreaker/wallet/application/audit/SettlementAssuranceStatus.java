package io.ipbreaker.wallet.application.audit;

import java.time.Instant;
import java.util.List;

public record SettlementAssuranceStatus(
        String overallStatus,
        String controlStatus,
        String settlementStatus,
        String indexStatus,
        String reconciliationStatus,
        int openReconciliationDifferenceCount,
        Instant lastReconciledAt,
        List<SettlementAssuranceFacts.ReconciliationCheckView> reconciliationChecks,
        List<String> riskCodes,
        List<SettlementAssuranceFacts.ReconciliationDifferenceView> reconciliationDifferences,
        Long safeBlockNumber,
        String safeBlockHash) {
}
