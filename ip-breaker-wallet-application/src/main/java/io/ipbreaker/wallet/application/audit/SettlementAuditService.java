package io.ipbreaker.wallet.application.audit;

import java.math.BigInteger;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementAuditService {
    private static final String PACKAGE_VERSION = "SETTLEMENT_PROOF_V1";
    private static final String DISCLAIMER = "This package is a reproducible backend audit snapshot "
            + "with a cryptographic content digest. It is not a digital signature, legal opinion, "
            + "or independent assurance report.";

    private final SettlementAuditRepository repository;

    public SettlementAuditService(SettlementAuditRepository repository) {
        this.repository = repository;
    }

    public SettlementAuditTrail trail(String networkCode, BigInteger agreementId) {
        validate(agreementId);
        return repository.findTrail(normalize(networkCode), agreementId)
                .orElseThrow(SettlementAuditNotFoundException::new);
    }

    public SettlementAssuranceStatus assurance(String networkCode, BigInteger agreementId) {
        validate(agreementId);
        SettlementAssuranceFacts facts = repository.findAssuranceFacts(
                        normalize(networkCode), agreementId)
                .orElseThrow(SettlementAuditNotFoundException::new);
        return SettlementAssuranceClassifier.classify(facts);
    }

    @Transactional
    public SettlementProofPackage generate(String networkCode, BigInteger agreementId) {
        String network = normalize(networkCode);
        SettlementAuditTrail trail = trail(network, agreementId);
        SettlementAssuranceStatus assurance = assurance(network, agreementId);
        ProofMaterial material = new ProofMaterial(PACKAGE_VERSION, trail, assurance);
        SettlementProofHasher.HashedContent hashed = SettlementProofHasher.hash(material);
        SettlementAuditRepository.StoredProof stored = repository.storeProof(
                network, agreementId, PACKAGE_VERSION, hashed.contentHash(), hashed.canonicalJson());
        return new SettlementProofPackage(
                stored.id(), PACKAGE_VERSION, "SHA-256", hashed.contentHash(), stored.generatedAt(),
                network, agreementId, trail, assurance,
                new SettlementProofPackage.Disclaimer(true, false, false, DISCLAIMER));
    }

    private void validate(BigInteger agreementId) {
        if (agreementId == null || agreementId.signum() < 0) {
            throw new IllegalArgumentException("Agreement ID must be non-negative");
        }
    }

    private String normalize(String networkCode) {
        if (networkCode == null || networkCode.isBlank()) {
            throw new IllegalArgumentException("Network code is required");
        }
        return networkCode.toUpperCase(Locale.ROOT);
    }

    private record ProofMaterial(
            String packageVersion,
            SettlementAuditTrail auditTrail,
            SettlementAssuranceStatus assuranceStatus) {
    }
}
