package io.ipbreaker.wallet.application.audit;

import java.math.BigInteger;
import java.time.Instant;

public record SettlementProofPackage(
        long proofPackageId,
        String packageVersion,
        String hashAlgorithm,
        String contentHash,
        Instant generatedAt,
        String network,
        BigInteger agreementId,
        SettlementAuditTrail auditTrail,
        SettlementAssuranceStatus assuranceStatus,
        Disclaimer disclaimer) {

    public record Disclaimer(
            boolean cryptographicContentDigest,
            boolean digitalSignature,
            boolean legalOpinion,
            String statement) {
    }
}
