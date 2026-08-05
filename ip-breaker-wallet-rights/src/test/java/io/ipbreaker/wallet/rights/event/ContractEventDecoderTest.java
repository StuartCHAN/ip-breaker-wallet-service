package io.ipbreaker.wallet.rights.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ipbreaker.wallet.rights.contract.ContractType;
import io.ipbreaker.wallet.rights.contract.ManagedContract;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractEventDecoderTest {
    private static final String TRANSFER =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String CONTRACT = "0x1111111111111111111111111111111111111111";

    private final ContractEventDecoder decoder = new ContractEventDecoder();
    private final ManagedContract assetRegistry = new ManagedContract(
            1L, 1L, ContractType.IP_ASSET_REGISTRY, CONTRACT,
            ContractEventDecoder.DECODER_VERSION, 100L, hash(99));

    @Test
    void decodesAssetTransferWithUint256Identifier() {
        BigInteger tokenId = BigInteger.ONE.shiftLeft(200);
        DecodedContractEvent decoded = decoder.decode(assetRegistry, log(List.of(
                TRANSFER, addressTopic("0x2222222222222222222222222222222222222222"),
                addressTopic("0x3333333333333333333333333333333333333333"),
                uintTopic(tokenId))));

        assertEquals(DomainEventType.IP_ASSET_TRANSFERRED, decoded.eventType());
        assertEquals(tokenId, decoded.aggregateId());
        assertEquals("0x3333333333333333333333333333333333333333", decoded.payload().get("to"));
    }

    @Test
    void unknownTopicIsPreservedWithoutProjectionIdentity() {
        DecodedContractEvent decoded = decoder.decode(assetRegistry, log(List.of(hash(123))));

        assertEquals(DomainEventType.UNKNOWN_CONTRACT_EVENT, decoded.eventType());
        assertEquals("TOPIC_NOT_IN_ABI", decoded.projectionErrorCode());
    }

    @Test
    void malformedKnownTopicFailsInsteadOfBecomingUnknown() {
        assertThrows(KnownEventDecodingException.class,
                () -> decoder.decode(assetRegistry, log(List.of(TRANSFER))));
    }

    private LogEnvelope log(List<String> topics) {
        return new LogEnvelope(1L, hash(101), 101L, Instant.EPOCH, hash(55), 0, "0x",
                CONTRACT, 0, topics, "0x");
    }

    private static String addressTopic(String address) {
        return "0x" + "0".repeat(24) + address.substring(2);
    }

    private static String uintTopic(BigInteger value) {
        return "0x" + String.format("%064x", value);
    }

    private static String hash(long value) {
        return "0x" + String.format("%064x", value);
    }
}
