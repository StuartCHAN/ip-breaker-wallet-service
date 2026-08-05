package io.ipbreaker.wallet.application.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.rights.event.ChainDomainEvent;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@Service
public class SettlementEligibilityService {
    private final SettlementEligibilityRepository repository;
    private final ObjectMapper canonicalMapper = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    public SettlementEligibilityService(SettlementEligibilityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ObligationView register(
            String networkCode, BigInteger agreementId, TermsManifest manifest) {
        validate(agreementId, manifest);
        TermsManifestHash prepared = prepare(manifest);
        return repository.registerManifest(
                networkCode, agreementId, manifest, prepared.manifestHash(), prepared.canonicalJson());
    }

    public TermsManifestHash prepare(TermsManifest manifest) {
        validate(BigInteger.ZERO, manifest);
        String canonicalJson = canonicalJson(manifest);
        String hash = Numeric.toHexString(Hash.sha3(canonicalJson.getBytes(StandardCharsets.UTF_8)))
                .toLowerCase(Locale.ROOT);
        return new TermsManifestHash(hash, canonicalJson);
    }

    public ObligationView find(String networkCode, BigInteger agreementId) {
        return repository.find(networkCode, agreementId).orElseThrow(SettlementNotFoundException::new);
    }

    @Transactional
    public void onCanonicalEvent(ChainDomainEvent event) {
        repository.evaluate(event);
    }

    private void validate(BigInteger agreementId, TermsManifest manifest) {
        if (agreementId.signum() < 0 || manifest.schemaVersion() != 1 || manifest.termsVersion() < 1
                || manifest.assetId().signum() < 0 || manifest.amount().signum() <= 0) {
            throw new IllegalArgumentException("Invalid structured terms manifest");
        }
        if (!"NATIVE".equals(manifest.currency())) {
            throw new IllegalArgumentException("LicenseEscrow only supports NATIVE currency");
        }
        for (String address : new String[] {
                manifest.licensor(), manifest.licensee(), manifest.payer(), manifest.payee()}) {
            if (address == null || !address.matches("0x[0-9a-fA-F]{40}")) {
                throw new IllegalArgumentException("Manifest contains an invalid address");
            }
        }
    }

    private String canonicalJson(TermsManifest manifest) {
        Map<String, Object> fields = new TreeMap<>();
        fields.put("amount", manifest.amount().toString());
        fields.put("assetId", manifest.assetId().toString());
        fields.put("currency", manifest.currency());
        fields.put("licensee", manifest.licensee().toLowerCase(Locale.ROOT));
        fields.put("licensor", manifest.licensor().toLowerCase(Locale.ROOT));
        fields.put("payee", manifest.payee().toLowerCase(Locale.ROOT));
        fields.put("payer", manifest.payer().toLowerCase(Locale.ROOT));
        fields.put("schemaVersion", manifest.schemaVersion());
        fields.put("termsVersion", manifest.termsVersion());
        try {
            return canonicalMapper.writeValueAsString(fields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize terms manifest", exception);
        }
    }
}
