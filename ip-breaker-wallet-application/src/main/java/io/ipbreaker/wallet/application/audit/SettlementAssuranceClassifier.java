package io.ipbreaker.wallet.application.audit;

import java.util.ArrayList;
import java.util.List;

public final class SettlementAssuranceClassifier {
    private SettlementAssuranceClassifier() {
    }

    public static SettlementAssuranceStatus classify(SettlementAssuranceFacts facts) {
        List<String> risks = new ArrayList<>();
        if (facts.activeContractCount() != 3 || facts.incompleteBackfillCount() > 0) {
            risks.add("RIGHTS_INDEX_NOT_READY");
        }
        if (facts.runningRebuildCount() > 0) {
            risks.add("PROJECTION_REBUILD_RUNNING");
        }
        if (facts.orphanedCurrentSnapshotCount() > 0) {
            risks.add("ORPHANED_CURRENT_ELIGIBILITY_SNAPSHOT");
        }
        if (facts.unbalancedJournalCount() > 0) {
            risks.add("UNBALANCED_SETTLEMENT_JOURNAL");
        }
        if (!facts.reconciliationDifferences().isEmpty()) {
            risks.add("OPEN_RECONCILIATION_DIFFERENCE");
        }
        if (facts.completedReconciliationCheckCount() != 3) {
            risks.add("RECONCILIATION_NOT_RUN");
        }
        if (facts.unknownEventCount() > 0) {
            risks.add("UNKNOWN_CONTRACT_EVENT");
        }
        if ("HELD".equals(facts.controlStatus())) {
            risks.add("SETTLEMENT_HELD");
        }
        if ("DISPUTED".equals(facts.controlStatus())) {
            risks.add("SETTLEMENT_DISPUTED");
        }
        if ("REVERSED".equals(facts.settlementStatus())) {
            risks.add("REVERSED_AWAITING_CANONICAL_RESTORE");
        }
        if ("PENDING".equals(facts.settlementStatus())) {
            risks.add("PAYMENT_NOT_ELIGIBLE");
        }
        if ("ELIGIBLE".equals(facts.settlementStatus())) {
            risks.add("ELIGIBLE_NOT_POSTED");
        }
        String indexStatus = facts.runningRebuildCount() > 0 ? "REBUILDING"
                : facts.activeContractCount() == 3 && facts.incompleteBackfillCount() == 0
                        ? "READY" : "NOT_READY";
        String reconciliationStatus = facts.completedReconciliationCheckCount() != 3 ? "UNKNOWN"
                : facts.reconciliationDifferences().isEmpty()
                        && facts.unbalancedJournalCount() == 0 ? "MATCHED" : "DIFFERENCE";
        boolean critical = risks.contains("UNBALANCED_SETTLEMENT_JOURNAL")
                || risks.contains("ORPHANED_CURRENT_ELIGIBILITY_SNAPSHOT")
                || risks.contains("OPEN_RECONCILIATION_DIFFERENCE");
        boolean blocked = risks.contains("RIGHTS_INDEX_NOT_READY")
                || risks.contains("PROJECTION_REBUILD_RUNNING")
                || risks.contains("RECONCILIATION_NOT_RUN")
                || risks.contains("SETTLEMENT_HELD") || risks.contains("SETTLEMENT_DISPUTED");
        String overall = critical ? "CRITICAL" : blocked ? "BLOCKED"
                : risks.isEmpty() ? "CLEAR" : "ATTENTION";
        return new SettlementAssuranceStatus(
                overall, facts.controlStatus(), facts.settlementStatus(), indexStatus,
                reconciliationStatus, facts.reconciliationDifferences().size(), facts.lastReconciledAt(),
                facts.reconciliationChecks(), List.copyOf(risks), facts.reconciliationDifferences(),
                facts.safeBlockNumber(), facts.safeBlockHash());
    }
}
