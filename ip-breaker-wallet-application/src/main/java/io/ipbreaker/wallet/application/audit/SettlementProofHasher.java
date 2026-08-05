package io.ipbreaker.wallet.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SettlementProofHasher {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private SettlementProofHasher() {
    }

    public static HashedContent hash(Object material) {
        try {
            String canonicalJson = MAPPER.writeValueAsString(material);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = "0x" + HexFormat.of().formatHex(
                    digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
            return new HashedContent(hash, canonicalJson);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to create settlement proof digest", exception);
        }
    }

    public record HashedContent(String contentHash, String canonicalJson) {
    }
}
