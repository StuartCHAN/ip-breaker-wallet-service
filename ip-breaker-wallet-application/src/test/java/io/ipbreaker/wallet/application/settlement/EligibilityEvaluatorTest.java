package io.ipbreaker.wallet.application.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class EligibilityEvaluatorTest {
    private static final String LICENSOR = "0x1111111111111111111111111111111111111111";
    private static final String LICENSEE = "0x2222222222222222222222222222222222222222";

    @Test
    void acceptsFactsThatMatchCanonicalAgreementAndPayment() {
        EligibilityResult result = EligibilityEvaluator.evaluate(validFacts());

        assertEquals(EligibilityDecision.ELIGIBLE, result.decision());
        assertEquals(List.of(), result.reasonCodes());
    }

    @Test
    void reportsDeterministicReasonsForMissingPaymentAndWrongOwner() {
        EligibilityFacts valid = validFacts();
        EligibilityFacts invalid = new EligibilityFacts(
                LICENSEE, valid.assetStatus(), valid.licensor(), valid.licensee(),
                valid.manifestHash(), valid.chainTermsHash(), valid.agreementStatus(), valid.assetId(),
                valid.agreementAssetId(), valid.payer(), valid.payee(), valid.currency(), valid.amount(),
                valid.licenseFee(), null, null);

        EligibilityResult result = EligibilityEvaluator.evaluate(invalid);

        assertEquals(EligibilityDecision.INELIGIBLE, result.decision());
        assertEquals(List.of(
                EligibilityReasonCode.LICENSOR_NOT_CURRENT_OWNER,
                EligibilityReasonCode.PAYMENT_NOT_OBSERVED), result.reasonCodes());
    }

    @Test
    void rejectsDisputedAgreementWithoutReclassifyingThePayment() {
        EligibilityFacts valid = validFacts();
        EligibilityFacts disputed = new EligibilityFacts(
                valid.assetOwner(), valid.assetStatus(), valid.licensor(), valid.licensee(),
                valid.manifestHash(), valid.chainTermsHash(), "DISPUTED", valid.assetId(),
                valid.agreementAssetId(), valid.payer(), valid.payee(), valid.currency(), valid.amount(),
                valid.licenseFee(), valid.paymentPayer(), valid.paymentAmount());

        EligibilityResult result = EligibilityEvaluator.evaluate(disputed);

        assertEquals(List.of(EligibilityReasonCode.AGREEMENT_NOT_SETTLEABLE), result.reasonCodes());
    }

    private EligibilityFacts validFacts() {
        return new EligibilityFacts(
                LICENSOR, "ACTIVE", LICENSOR, LICENSEE, "0xabc", "0xabc", "FUNDED",
                BigInteger.ONE, BigInteger.ONE, LICENSEE, LICENSOR, "NATIVE",
                BigInteger.valueOf(1000), BigInteger.valueOf(1000), LICENSEE,
                BigInteger.valueOf(1000));
    }
}
