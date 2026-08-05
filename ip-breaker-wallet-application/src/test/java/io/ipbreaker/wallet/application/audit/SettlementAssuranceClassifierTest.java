package io.ipbreaker.wallet.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementAssuranceClassifierTest {
    @Test
    void reportsClearOnlyWhenIndexAndReconciliationAreClean() {
        SettlementAssuranceFacts facts = new SettlementAssuranceFacts(
                "CLEAR", "SETTLED", 3, 0, 0, 0, 0, 0, 3, List.of(), List.of(),
                null, 100L, "0xabc");

        SettlementAssuranceStatus result = SettlementAssuranceClassifier.classify(facts);

        assertEquals("CLEAR", result.overallStatus());
        assertEquals("READY", result.indexStatus());
        assertEquals("MATCHED", result.reconciliationStatus());
        assertTrue(result.riskCodes().isEmpty());
    }

    @Test
    void treatsAnOrphanedCurrentSnapshotAsCritical() {
        SettlementAssuranceFacts facts = new SettlementAssuranceFacts(
                "CLEAR", "REVERSED", 3, 0, 0, 1, 0, 0, 3, List.of(), List.of(),
                null, 100L, "0xabc");

        SettlementAssuranceStatus result = SettlementAssuranceClassifier.classify(facts);

        assertEquals("CRITICAL", result.overallStatus());
        assertTrue(result.riskCodes().contains("ORPHANED_CURRENT_ELIGIBILITY_SNAPSHOT"));
        assertTrue(result.riskCodes().contains("REVERSED_AWAITING_CANONICAL_RESTORE"));
    }

    @Test
    void keepsLegalControlSeparateFromTechnicalReconciliation() {
        SettlementAssuranceFacts facts = new SettlementAssuranceFacts(
                "DISPUTED", "SETTLED", 3, 0, 0, 0, 0, 0, 3, List.of(), List.of(),
                null, 100L, "0xabc");

        SettlementAssuranceStatus result = SettlementAssuranceClassifier.classify(facts);

        assertEquals("BLOCKED", result.overallStatus());
        assertEquals("MATCHED", result.reconciliationStatus());
        assertTrue(result.riskCodes().contains("SETTLEMENT_DISPUTED"));
    }

    @Test
    void incompletePaymentLifecycleNeedsAttention() {
        SettlementAssuranceFacts facts = new SettlementAssuranceFacts(
                "CLEAR", "PENDING", 3, 0, 0, 0, 0, 0, 3, List.of(), List.of(),
                null, 100L, "0xabc");

        SettlementAssuranceStatus result = SettlementAssuranceClassifier.classify(facts);

        assertEquals("ATTENTION", result.overallStatus());
        assertTrue(result.riskCodes().contains("PAYMENT_NOT_ELIGIBLE"));
    }

    @Test
    void absenceOfCompletedChecksIsNotReportedAsMatched() {
        SettlementAssuranceFacts facts = new SettlementAssuranceFacts(
                "CLEAR", "SETTLED", 3, 0, 0, 0, 0, 0, 0, List.of(), List.of(),
                null, 100L, "0xabc");

        SettlementAssuranceStatus result = SettlementAssuranceClassifier.classify(facts);

        assertEquals("BLOCKED", result.overallStatus());
        assertEquals("UNKNOWN", result.reconciliationStatus());
        assertTrue(result.riskCodes().contains("RECONCILIATION_NOT_RUN"));
    }
}
