package io.ipbreaker.wallet.application.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementEligibilityServiceTest {
    private static final String LICENSOR = "0x1111111111111111111111111111111111111111";
    private static final String LICENSEE = "0x2222222222222222222222222222222222222222";

    @Test
    void canonicalHashIsDeterministicAndNormalizesAddresses() {
        FakeRepository repository = new FakeRepository();
        SettlementEligibilityService service = new SettlementEligibilityService(repository);
        TermsManifest lower = manifest(LICENSOR, LICENSEE, "NATIVE");
        TermsManifest upper = manifest("0x" + LICENSOR.substring(2).toUpperCase(),
                "0x" + LICENSEE.substring(2).toUpperCase(), "NATIVE");

        TermsManifestHash first = service.prepare(lower);
        TermsManifestHash second = service.prepare(upper);

        assertEquals(first, second);
        assertTrue(first.manifestHash().matches("0x[0-9a-f]{64}"));
        assertTrue(first.canonicalJson().contains("\"amount\":\"1000\""));
    }

    @Test
    void rejectsCurrencyNotSupportedByLicenseEscrow() {
        SettlementEligibilityService service = new SettlementEligibilityService(new FakeRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.prepare(manifest(LICENSOR, LICENSEE, "ERC20")));
    }

    @Test
    void registrationPassesCanonicalHashToRepository() {
        FakeRepository repository = new FakeRepository();
        SettlementEligibilityService service = new SettlementEligibilityService(repository);
        TermsManifest terms = manifest(LICENSOR, LICENSEE, "NATIVE");

        service.register("SEPOLIA", BigInteger.TEN, terms);

        assertEquals(service.prepare(terms).manifestHash(), repository.hash);
        assertEquals(BigInteger.TEN, repository.agreementId);
    }

    private TermsManifest manifest(String licensor, String licensee, String currency) {
        return new TermsManifest(1, 1, BigInteger.ONE, licensor, licensee, licensee, licensor,
                currency, BigInteger.valueOf(1000));
    }

    private static final class FakeRepository implements SettlementEligibilityRepository {
        private String hash;
        private BigInteger agreementId;

        @Override
        public ObligationView registerManifest(
                String networkCode, BigInteger requestedAgreementId, TermsManifest manifest,
                String manifestHash, String canonicalJson) {
            hash = manifestHash;
            agreementId = requestedAgreementId;
            return null;
        }

        @Override
        public Optional<ObligationView> find(String networkCode, BigInteger requestedAgreementId) {
            return Optional.empty();
        }

        @Override
        public void evaluate(ChainDomainEvent event) {
        }

        @Override
        public void rollbackAfter(long networkId, long ancestorBlock, String ancestorBlockHash) {
        }
    }
}
