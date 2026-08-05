package io.ipbreaker.wallet.rights.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ipbreaker.wallet.rights.contract.ContractType;
import io.ipbreaker.wallet.rights.contract.ManagedContract;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public final class ContractEventDecoder {
    public static final String DECODER_VERSION = "rwa-9adaba8-v1";

    private static final String TRANSFER =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String ASSET_REGISTERED =
            "0x863ab6e354014c2a1ca783a91da75f20af1b09b4f2594da9ba456116ec107167";
    private static final String EVIDENCE_ADDED =
            "0x8c72d808f552d69918cd5b6e406961748a6d491203b553499d8bea1e49d38020";
    private static final String EVIDENCE_STATUS =
            "0x605695414ddf0e18c8dcacdfe01bc1f54e98538eafa84ff7e102c6c4cbff4ab4";
    private static final String OFFER_CREATED =
            "0x95f00b484518ad033a3e6dc51367bc1542f65c525ecfb9d9490ec75ddd4cd11b";
    private static final String OFFER_STATUS =
            "0x8c00974b4ba6f52ac52c8f32ffcb9a779fbf37c3c0313e08d0143e8fc49245da";
    private static final String LICENSE_PURCHASED =
            "0xc047b78e9b27feee92adad27d6dd79a74a3420b7f611ec3c64cd120c65c89b04";
    private static final String AGREEMENT_CREATED =
            "0x5d4474053cce07c7f503b56d92f6c4e52c3183c8a07171d1e4f7b39fbf050730";
    private static final String LICENSE_STATUS =
            "0x507dbf037e88f3be9cdd03f4f17707c38495217d1809db44666758877e50f433";
    private static final String LICENSE_FUNDED =
            "0xade3b0eda15bc87a421d05d259a638935623fe916d8f3980d670de867076f59d";
    private static final String PERFORMANCE_CONFIRMED =
            "0x1148624e934cace34e7bb3d474d855cde3594a35ed954458bd3f7ef1fb014dfc";
    private static final String FUNDS_RELEASED =
            "0x6e3c6096795c8298a218b2cfb8bde42726ff7c9a3d27b4d3ba41ab7f74feb5fb";
    private static final String DISPUTE_RAISED =
            "0x84a477df8a28a4276ca6dee4458a06c3015f30c477d9c949ede4e13ff8a552b4";
    private static final String DISPUTE_RESOLVED =
            "0x5add0b1fc2a3dfd95be33d1d5feb6709087e50213b7a73c3c3b2c387001221e7";
    private static final String AGREEMENT_CANCELLED =
            "0x0cae0523c4d6568388c55dcaeb921d927527a60000ca46adffedddf87225f0ec";
    private static final String ARBITER_UPDATED =
            "0xf0077b1d49976ecd154f8efdc46cca625489710840b8995fb53d9efa037353ba";

    private static final Set<String> INFRASTRUCTURE_TOPICS = Set.of(
            Hash.sha3String("Approval(address,address,uint256)"),
            Hash.sha3String("ApprovalForAll(address,address,bool)"),
            Hash.sha3String("OwnershipTransferred(address,address)"));

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private final AssetJurisdictionResolver jurisdictionResolver;

    public ContractEventDecoder() {
        this((address, assetId, blockNumber) -> {
            throw new KnownEventDecodingException("Block-pinned jurisdiction resolver is unavailable");
        });
    }

    public ContractEventDecoder(AssetJurisdictionResolver jurisdictionResolver) {
        this.jurisdictionResolver = jurisdictionResolver;
    }

    public DecodedContractEvent decode(ManagedContract contract, LogEnvelope log) {
        validateEnvelope(contract, log);
        String topic = normalizeHash(log.topics().getFirst());
        try {
            if (topic.equals(TRANSFER)) {
                return transfer(contract, log);
            }
            if (contract.type() == ContractType.IP_ASSET_REGISTRY && topic.equals(ASSET_REGISTERED)) {
                return assetRegistered(contract, log);
            }
            if (contract.type() == ContractType.EVIDENCE_REGISTRY && topic.equals(EVIDENCE_ADDED)) {
                return evidenceAdded(contract, log);
            }
            if (contract.type() == ContractType.EVIDENCE_REGISTRY && topic.equals(EVIDENCE_STATUS)) {
                return evidenceStatus(contract, log);
            }
            if (contract.type() == ContractType.LICENSE_ESCROW) {
                return licenseEvent(contract, log, topic);
            }
            if (INFRASTRUCTURE_TOPICS.contains(topic)) {
                return decoded(contract, log, "InfrastructureEvent",
                        DomainEventType.KNOWN_INFRASTRUCTURE_EVENT, null, null, null, Map.of(), null);
            }
            return unknown(contract, log);
        } catch (KnownEventDecodingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KnownEventDecodingException("Malformed known event " + topic, exception);
        }
    }

    private DecodedContractEvent assetRegistered(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 3);
        List<Type> data = decodeData(log, refs(Utf8String.class, Utf8String.class, Bytes32.class,
                Utf8String.class));
        BigInteger assetId = uintTopic(log, 1);
        Jurisdiction jurisdiction = jurisdiction(log, assetId, contract.address());
        Map<String, Object> payload = payload(
                "assetId", assetId.toString(),
                "owner", addressTopic(log, 2),
                "title", value(data, 0),
                "assetType", value(data, 1),
                "documentHash", bytes32(data, 2),
                "metadataURI", value(data, 3),
                "jurisdiction", jurisdiction.value(),
                "enrichmentSource", jurisdiction.source());
        return decoded(contract, log, "IPAssetRegistered", DomainEventType.IP_ASSET_REGISTERED,
                AggregateType.IP_ASSET, assetId, assetId, payload, null);
    }

    private DecodedContractEvent transfer(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        BigInteger tokenId = uintTopic(log, 3);
        DomainEventType type = contract.type() == ContractType.IP_ASSET_REGISTRY
                ? DomainEventType.IP_ASSET_TRANSFERRED
                : DomainEventType.LICENSE_CERTIFICATE_TRANSFERRED;
        AggregateType aggregate = contract.type() == ContractType.IP_ASSET_REGISTRY
                ? AggregateType.IP_ASSET : AggregateType.LICENSE_CERTIFICATE;
        BigInteger relatedAsset = contract.type() == ContractType.IP_ASSET_REGISTRY ? tokenId : null;
        return decoded(contract, log, "Transfer", type, aggregate, tokenId, relatedAsset,
                payload("from", addressTopic(log, 1), "to", addressTopic(log, 2),
                        "tokenId", tokenId.toString()), null);
    }

    private DecodedContractEvent evidenceAdded(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        List<Type> data = decodeData(log, refs(Utf8String.class, Bytes32.class, Utf8String.class,
                Bytes32.class));
        BigInteger assetId = uintTopic(log, 1);
        BigInteger evidenceId = uintTopic(log, 2);
        return decoded(contract, log, "EvidenceAdded", DomainEventType.EVIDENCE_ADDED,
                AggregateType.EVIDENCE, evidenceId, assetId,
                payload("assetId", assetId.toString(), "evidenceId", evidenceId.toString(),
                        "submittedBy", addressTopic(log, 3), "evidenceType", value(data, 0),
                        "evidenceHash", bytes32(data, 1), "evidenceURI", value(data, 2),
                        "attestationUID", bytes32(data, 3)), null);
    }

    private DecodedContractEvent evidenceStatus(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        List<Type> data = decodeData(log, refs(Address.class));
        BigInteger evidenceId = uintTopic(log, 1);
        int previous = uintTopic(log, 2).intValueExact();
        int next = uintTopic(log, 3).intValueExact();
        evidenceStatus(previous);
        evidenceStatus(next);
        return decoded(contract, log, "EvidenceStatusChanged",
                DomainEventType.EVIDENCE_STATUS_CHANGED, AggregateType.EVIDENCE, evidenceId, null,
                payload("evidenceId", evidenceId.toString(), "previousStatus", evidenceStatus(previous),
                        "newStatus", evidenceStatus(next), "reviewedBy", normalizeAddress(value(data, 0))), null);
    }

    private DecodedContractEvent licenseEvent(
            ManagedContract contract, LogEnvelope log, String topic) {
        return switch (topic) {
            case OFFER_CREATED -> offerCreated(contract, log);
            case OFFER_STATUS -> offerStatus(contract, log);
            case LICENSE_PURCHASED -> licensePurchased(contract, log);
            case AGREEMENT_CREATED -> agreementCreated(contract, log);
            case LICENSE_STATUS -> licenseStatus(contract, log);
            case LICENSE_FUNDED -> agreementAddressAmount(contract, log, "LicenseFunded",
                    DomainEventType.LICENSE_FUNDED, "fundedBy");
            case PERFORMANCE_CONFIRMED -> agreementAddress(contract, log, "PerformanceConfirmed",
                    DomainEventType.LICENSE_PERFORMANCE_CONFIRMED, "confirmedBy");
            case FUNDS_RELEASED -> agreementAddressAmount(contract, log, "FundsReleased",
                    DomainEventType.LICENSE_FUNDS_RELEASED, "to");
            case DISPUTE_RAISED -> agreementAddress(contract, log, "DisputeRaised",
                    DomainEventType.LICENSE_DISPUTE_RAISED, "raisedBy");
            case DISPUTE_RESOLVED -> disputeResolved(contract, log);
            case AGREEMENT_CANCELLED -> agreementCancelled(contract, log);
            case ARBITER_UPDATED -> arbiterUpdated(contract, log);
            default -> INFRASTRUCTURE_TOPICS.contains(topic)
                    ? decoded(contract, log, "InfrastructureEvent",
                            DomainEventType.KNOWN_INFRASTRUCTURE_EVENT, null, null, null, Map.of(), null)
                    : unknown(contract, log);
        };
    }

    private DecodedContractEvent offerCreated(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        List<Type> data = decodeData(log, refs(Uint256.class, Uint64.class, Bytes32.class,
                Utf8String.class, Bool.class));
        BigInteger offerId = uintTopic(log, 1);
        BigInteger assetId = uintTopic(log, 2);
        return decoded(contract, log, "LicenseOfferCreated", DomainEventType.LICENSE_OFFER_CREATED,
                AggregateType.LICENSE_OFFER, offerId, assetId,
                payload("offerId", offerId.toString(), "assetId", assetId.toString(),
                        "licensor", addressTopic(log, 3), "price", number(data, 0),
                        "duration", number(data, 1), "termsHash", bytes32(data, 2),
                        "termsURI", value(data, 3), "active", value(data, 4)), null);
    }

    private DecodedContractEvent offerStatus(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 2);
        BigInteger offerId = uintTopic(log, 1);
        List<Type> data = decodeData(log, refs(Bool.class));
        return decoded(contract, log, "LicenseOfferStatusUpdated",
                DomainEventType.LICENSE_OFFER_STATUS_UPDATED, AggregateType.LICENSE_OFFER,
                offerId, null, payload("offerId", offerId.toString(), "active", value(data, 0)),
                "MISSING_OFFER_ORIGIN");
    }

    private DecodedContractEvent licensePurchased(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        List<Type> data = decodeData(log, refs(Address.class, Address.class, Uint256.class,
                Uint256.class, Uint256.class));
        BigInteger licenseId = uintTopic(log, 1);
        BigInteger offerId = uintTopic(log, 2);
        BigInteger assetId = uintTopic(log, 3);
        return decoded(contract, log, "LicensePurchased", DomainEventType.LICENSE_PURCHASED,
                AggregateType.LICENSE_CERTIFICATE, licenseId, assetId,
                payload("licenseId", licenseId.toString(), "offerId", offerId.toString(),
                        "assetId", assetId.toString(), "licensor", normalizeAddress(value(data, 0)),
                        "licensee", normalizeAddress(value(data, 1)), "price", number(data, 2),
                        "validFrom", number(data, 3), "validUntil", number(data, 4)), null);
    }

    private DecodedContractEvent agreementCreated(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        List<Type> data = decodeData(log, refs(Address.class, Address.class, Uint256.class,
                Bytes32.class, Utf8String.class));
        BigInteger agreementId = uintTopic(log, 1);
        BigInteger assetId = uintTopic(log, 2);
        return decoded(contract, log, "LicenseAgreementCreated",
                DomainEventType.LICENSE_AGREEMENT_CREATED, AggregateType.LICENSE_AGREEMENT,
                agreementId, assetId,
                payload("agreementId", agreementId.toString(), "assetId", assetId.toString(),
                        "licensor", addressTopic(log, 3), "licensee", normalizeAddress(value(data, 0)),
                        "arbiter", normalizeAddress(value(data, 1)), "licenseFee", number(data, 2),
                        "termsHash", bytes32(data, 3), "termsURI", value(data, 4)), null);
    }

    private DecodedContractEvent licenseStatus(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 4);
        BigInteger agreementId = uintTopic(log, 1);
        int previous = uintTopic(log, 2).intValueExact();
        int next = uintTopic(log, 3).intValueExact();
        return decoded(contract, log, "LicenseStatusChanged", DomainEventType.LICENSE_STATUS_CHANGED,
                AggregateType.LICENSE_AGREEMENT, agreementId, null,
                payload("agreementId", agreementId.toString(), "previousStatus", licenseStatus(previous),
                        "newStatus", licenseStatus(next)), null);
    }

    private DecodedContractEvent agreementAddressAmount(ManagedContract contract, LogEnvelope log,
            String name, DomainEventType type, String addressField) {
        requireTopics(log, 3);
        List<Type> data = decodeData(log, refs(Uint256.class));
        BigInteger agreementId = uintTopic(log, 1);
        return decoded(contract, log, name, type, AggregateType.LICENSE_AGREEMENT, agreementId, null,
                payload("agreementId", agreementId.toString(), addressField, addressTopic(log, 2),
                        "amount", number(data, 0)), null);
    }

    private DecodedContractEvent agreementAddress(ManagedContract contract, LogEnvelope log,
            String name, DomainEventType type, String addressField) {
        requireTopics(log, 3);
        BigInteger agreementId = uintTopic(log, 1);
        return decoded(contract, log, name, type, AggregateType.LICENSE_AGREEMENT, agreementId, null,
                payload("agreementId", agreementId.toString(), addressField, addressTopic(log, 2)), null);
    }

    private DecodedContractEvent disputeResolved(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 2);
        List<Type> data = decodeData(log, refs(Bool.class, Uint256.class));
        BigInteger agreementId = uintTopic(log, 1);
        return decoded(contract, log, "DisputeResolved", DomainEventType.LICENSE_DISPUTE_RESOLVED,
                AggregateType.LICENSE_AGREEMENT, agreementId, null,
                payload("agreementId", agreementId.toString(), "paidToLicensor", value(data, 0),
                        "amount", number(data, 1)), null);
    }

    private DecodedContractEvent agreementCancelled(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 2);
        BigInteger agreementId = uintTopic(log, 1);
        return decoded(contract, log, "AgreementCancelled",
                DomainEventType.LICENSE_AGREEMENT_CANCELLED, AggregateType.LICENSE_AGREEMENT,
                agreementId, null, payload("agreementId", agreementId.toString()), null);
    }

    private DecodedContractEvent arbiterUpdated(ManagedContract contract, LogEnvelope log) {
        requireTopics(log, 3);
        return decoded(contract, log, "ArbiterUpdated",
                DomainEventType.LICENSE_GLOBAL_ARBITER_UPDATED, null, null, null,
                payload("previousArbiter", addressTopic(log, 1), "newArbiter", addressTopic(log, 2)), null);
    }

    private DecodedContractEvent unknown(ManagedContract contract, LogEnvelope log) {
        return decoded(contract, log, "UNKNOWN", DomainEventType.UNKNOWN_CONTRACT_EVENT,
                null, null, null, Map.of(), "TOPIC_NOT_IN_ABI");
    }

    private DecodedContractEvent decoded(ManagedContract contract, LogEnvelope log, String eventName,
            DomainEventType eventType, AggregateType aggregateType, BigInteger aggregateId,
            BigInteger relatedAssetId, Map<String, Object> payload, String projectionErrorCode) {
        return new DecodedContractEvent(contract, log, eventName, eventType, aggregateType,
                aggregateId, relatedAssetId, payload, hash(payload), projectionErrorCode);
    }

    private void validateEnvelope(ManagedContract contract, LogEnvelope log) {
        if (!normalizeAddress(log.contractAddress()).equals(contract.address().toLowerCase(Locale.ROOT))) {
            throw new KnownEventDecodingException("Log address does not match managed contract");
        }
        if (log.topics().isEmpty()) {
            throw new KnownEventDecodingException("Contract log has no topic0");
        }
    }

    private void requireTopics(LogEnvelope log, int count) {
        if (log.topics().size() != count) {
            throw new KnownEventDecodingException("Unexpected indexed topic count");
        }
    }

    private List<Type> decodeData(LogEnvelope log, List<TypeReference<?>> references) {
        List<Type> decoded = FunctionReturnDecoder.decode(log.data(), references);
        if (decoded.size() != references.size()) {
            throw new KnownEventDecodingException("Unexpected event data field count");
        }
        return decoded;
    }

    @SafeVarargs
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final List<TypeReference<?>> refs(Class<? extends Type>... types) {
        java.util.ArrayList<TypeReference<?>> references = new java.util.ArrayList<>();
        for (Class<? extends Type> type : types) {
            references.add(TypeReference.create((Class) type));
        }
        return references;
    }

    private BigInteger uintTopic(LogEnvelope log, int index) {
        return Numeric.toBigInt(normalizeHash(log.topics().get(index)));
    }

    private String addressTopic(LogEnvelope log, int index) {
        String clean = Numeric.cleanHexPrefix(normalizeHash(log.topics().get(index)));
        if (clean.length() != 64) {
            throw new KnownEventDecodingException("Malformed indexed address");
        }
        return "0x" + clean.substring(24).toLowerCase(Locale.ROOT);
    }

    private Jurisdiction jurisdiction(LogEnvelope log, BigInteger assetId, String registryAddress) {
        String input = log.transactionInput();
        String selector = Hash.sha3String("registerAsset(string,string,string,bytes32,string)")
                .substring(0, 10);
        if (input == null || input.length() < 10 || !input.substring(0, 10).equalsIgnoreCase(selector)) {
            return new Jurisdiction(
                    jurisdictionResolver.resolve(registryAddress, assetId, log.blockNumber()),
                    "BLOCK_PINNED_CALL");
        }
        List<Type> decoded = FunctionReturnDecoder.decode("0x" + input.substring(10),
                refs(Utf8String.class, Utf8String.class, Utf8String.class, Bytes32.class,
                        Utf8String.class));
        if (decoded.size() != 5) {
            throw new KnownEventDecodingException("Unable to enrich jurisdiction from calldata");
        }
        return new Jurisdiction(value(decoded, 2).toString(), "CALLDATA");
    }

    private String evidenceStatus(int value) {
        return switch (value) {
            case 0 -> "SUBMITTED";
            case 1 -> "VERIFIED";
            case 2 -> "REJECTED";
            case 3 -> "REVOKED";
            default -> throw new KnownEventDecodingException("Unknown evidence status " + value);
        };
    }

    private String licenseStatus(int value) {
        return switch (value) {
            case 0 -> "CREATED";
            case 1 -> "FUNDED";
            case 2 -> "ACTIVE";
            case 3 -> "DISPUTED";
            case 4 -> "COMPLETED";
            case 5 -> "REFUNDED";
            case 6 -> "CANCELLED";
            default -> throw new KnownEventDecodingException("Unknown license status " + value);
        };
    }

    private Object value(List<Type> values, int index) {
        return values.get(index).getValue();
    }

    private String number(List<Type> values, int index) {
        return value(values, index).toString();
    }

    private String bytes32(List<Type> values, int index) {
        return Numeric.toHexString((byte[]) value(values, index)).toLowerCase(Locale.ROOT);
    }

    private String normalizeAddress(Object address) {
        String value = address.toString().toLowerCase(Locale.ROOT);
        return value.startsWith("0x") ? value : "0x" + value;
    }

    private String normalizeHash(String hash) {
        String value = hash.toLowerCase(Locale.ROOT);
        return value.startsWith("0x") ? value : "0x" + value;
    }

    private Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private String hash(Map<String, Object> payload) {
        try {
            byte[] json = objectMapper.writeValueAsString(new TreeMap<>(payload))
                    .getBytes(StandardCharsets.UTF_8);
            return Numeric.toHexString(Hash.sha3(json));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize event payload", exception);
        }
    }

    private record Jurisdiction(String value, String source) {
    }
}
