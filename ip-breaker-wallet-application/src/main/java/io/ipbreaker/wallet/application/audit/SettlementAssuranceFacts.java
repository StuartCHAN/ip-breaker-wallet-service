package io.ipbreaker.wallet.application.audit;

import java.time.Instant;
import java.util.List;

public record SettlementAssuranceFacts(
        String controlStatus,
        String settlementStatus,
        int activeContractCount,
        int incompleteBackfillCount,
        int runningRebuildCount,
        int orphanedCurrentSnapshotCount,
        int unknownEventCount,
        int unbalancedJournalCount,
        int completedReconciliationCheckCount,
        List<ReconciliationCheckView> reconciliationChecks,
        List<ReconciliationDifferenceView> reconciliationDifferences,
        Instant lastReconciledAt,
        Long safeBlockNumber,
        String safeBlockHash) {

    public record ReconciliationCheckView(
            String checkType,
            int differenceCount,
            Instant completedAt) {
    }

    public record ReconciliationDifferenceView(
            long id,
            String checkType,
            String assetCode,
            String subjectType,
            String subjectKey,
            String expectedAmount,
            String actualAmount,
            String details,
            long occurrenceCount,
            Instant lastDetectedAt) {
    }
}
