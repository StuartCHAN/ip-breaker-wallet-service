package io.ipbreaker.wallet.application.settlement;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

public record ObligationView(
        long obligationId,
        String network,
        String escrowAddress,
        BigInteger agreementId,
        long termsVersion,
        String termsManifestHash,
        BigInteger assetId,
        String payer,
        String payee,
        String currency,
        BigInteger amount,
        String settlementStatus,
        String controlStatus,
        Long paymentEventId,
        Long snapshotId,
        String eligibilityDecision,
        List<String> decisionReasonCodes,
        Long safeBlockNumber,
        String safeBlockHash,
        Instant updatedAt) {
}
