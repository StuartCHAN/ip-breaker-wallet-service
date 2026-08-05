package io.ipbreaker.wallet.application.audit;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public record SettlementAuditTrail(
        String network,
        BigInteger agreementId,
        Map<String, Object> chainState,
        List<Map<String, Object>> managedContracts,
        Map<String, Object> asset,
        List<Map<String, Object>> evidence,
        Map<String, Object> licenseAgreement,
        Map<String, Object> termsManifest,
        Map<String, Object> paymentObligation,
        Map<String, Object> paymentEvent,
        List<Map<String, Object>> eligibilitySnapshots,
        List<Map<String, Object>> allocationPlans,
        List<Map<String, Object>> allocationLines,
        List<Map<String, Object>> settlements,
        List<Map<String, Object>> ledgerTransactions,
        List<Map<String, Object>> ledgerEntries,
        List<Map<String, Object>> chainEvents) {
}
