package io.ipbreaker.wallet.application.audit;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

public interface SettlementAuditRepository {
    Optional<SettlementAuditTrail> findTrail(String networkCode, BigInteger agreementId);

    Optional<SettlementAssuranceFacts> findAssuranceFacts(
            String networkCode, BigInteger agreementId);

    StoredProof storeProof(
            String networkCode, BigInteger agreementId, String packageVersion,
            String contentHash, String packageJson);

    record StoredProof(long id, Instant generatedAt) {
    }
}
