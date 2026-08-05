package io.ipbreaker.wallet.application.settlement;

import java.util.List;

public record EligibilityResult(
        EligibilityDecision decision,
        List<EligibilityReasonCode> reasonCodes) {
}
