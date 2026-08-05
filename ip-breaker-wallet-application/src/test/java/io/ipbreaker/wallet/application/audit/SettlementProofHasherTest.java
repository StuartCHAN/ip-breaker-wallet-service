package io.ipbreaker.wallet.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementProofHasherTest {
    @Test
    void mapInsertionOrderDoesNotChangeDigest() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("agreementId", "42");
        first.put("status", "SETTLED");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("status", "SETTLED");
        second.put("agreementId", "42");

        SettlementProofHasher.HashedContent firstHash = SettlementProofHasher.hash(first);
        SettlementProofHasher.HashedContent secondHash = SettlementProofHasher.hash(second);

        assertEquals(firstHash, secondHash);
        assertTrue(firstHash.contentHash().matches("0x[0-9a-f]{64}"));
    }

    @Test
    void changedSettlementFactChangesDigest() {
        SettlementProofHasher.HashedContent settled = SettlementProofHasher.hash(
                Map.of("agreementId", "42", "status", "SETTLED"));
        SettlementProofHasher.HashedContent reversed = SettlementProofHasher.hash(
                Map.of("agreementId", "42", "status", "REVERSED"));

        assertNotEquals(settled.contentHash(), reversed.contentHash());
    }
}
