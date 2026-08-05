package io.ipbreaker.wallet.application.settlement;

import java.util.ArrayList;
import java.util.List;

public final class EligibilityEvaluator {
    private EligibilityEvaluator() {
    }

    public static EligibilityResult evaluate(EligibilityFacts facts) {
        List<EligibilityReasonCode> reasons = new ArrayList<>();
        if (facts.assetOwner() == null) {
            reasons.add(EligibilityReasonCode.ASSET_NOT_FOUND);
        } else {
            addUnless(reasons, "ACTIVE".equals(facts.assetStatus()),
                    EligibilityReasonCode.ASSET_NOT_ACTIVE);
            addUnless(reasons, facts.licensor().equals(facts.assetOwner()),
                    EligibilityReasonCode.LICENSOR_NOT_CURRENT_OWNER);
        }
        addUnless(reasons, facts.manifestHash().equals(facts.chainTermsHash()),
                EligibilityReasonCode.TERMS_HASH_MISMATCH);
        addUnless(reasons, List.of("CREATED", "FUNDED", "ACTIVE").contains(facts.agreementStatus()),
                EligibilityReasonCode.AGREEMENT_NOT_SETTLEABLE);
        addUnless(reasons, facts.assetId().equals(facts.agreementAssetId()),
                EligibilityReasonCode.ASSET_ID_MISMATCH);
        addUnless(reasons, facts.payer().equals(facts.licensee()),
                EligibilityReasonCode.PAYER_NOT_LICENSEE);
        addUnless(reasons, facts.payee().equals(facts.licensor()),
                EligibilityReasonCode.PAYEE_NOT_LICENSOR);
        addUnless(reasons, facts.amount().equals(facts.licenseFee()),
                EligibilityReasonCode.AMOUNT_MISMATCH);
        addUnless(reasons, "NATIVE".equals(facts.currency()),
                EligibilityReasonCode.CURRENCY_UNSUPPORTED);
        if (facts.paymentPayer() == null || facts.paymentAmount() == null) {
            reasons.add(EligibilityReasonCode.PAYMENT_NOT_OBSERVED);
        } else {
            addUnless(reasons, facts.payer().equals(facts.paymentPayer()),
                    EligibilityReasonCode.PAYMENT_PAYER_MISMATCH);
            addUnless(reasons, facts.amount().equals(facts.paymentAmount()),
                    EligibilityReasonCode.PAYMENT_AMOUNT_MISMATCH);
        }
        EligibilityDecision decision = reasons.isEmpty()
                ? EligibilityDecision.ELIGIBLE : EligibilityDecision.INELIGIBLE;
        return new EligibilityResult(decision, List.copyOf(reasons));
    }

    private static void addUnless(
            List<EligibilityReasonCode> reasons, boolean condition, EligibilityReasonCode reason) {
        if (!condition) {
            reasons.add(reason);
        }
    }
}
