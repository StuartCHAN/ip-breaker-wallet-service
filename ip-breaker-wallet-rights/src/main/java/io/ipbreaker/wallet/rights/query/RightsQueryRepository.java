package io.ipbreaker.wallet.rights.query;

import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository.EventCursor;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RightsQueryRepository {
    IndexState indexState(String networkCode);

    Optional<IpAssetView> findAsset(String networkCode, BigInteger assetId);

    Optional<LicenseAgreementView> findAgreement(String networkCode, BigInteger agreementId);

    List<TimelineEventView> findTimeline(
            String networkCode, BigInteger assetId, EventCursor cursor, int limit);

    record IpAssetView(
            String network,
            String registryAddress,
            BigInteger assetId,
            String owner,
            String title,
            String assetType,
            String jurisdiction,
            String documentHash,
            String metadataUri,
            String status,
            Instant registeredAt,
            long projectionVersion,
            long safeBlockNumber,
            String safeBlockHash,
            Map<String, Long> evidenceSummary,
            Map<String, Long> agreementSummary) {
    }

    record LicenseAgreementView(
            String network,
            String escrowAddress,
            BigInteger agreementId,
            BigInteger assetId,
            String licensor,
            String licensee,
            String arbiter,
            BigInteger licenseFee,
            BigInteger escrowedAmount,
            String termsHash,
            String termsUri,
            String status,
            Instant createdAt,
            Instant fundedAt,
            String releasedTo,
            BigInteger releasedAmount,
            long projectionVersion,
            long safeBlockNumber,
            String safeBlockHash) {
    }

    record TimelineEventView(
            long eventId,
            String eventType,
            String aggregateType,
            BigInteger aggregateId,
            long blockNumber,
            String blockHash,
            Instant blockTimestamp,
            String transactionHash,
            int transactionIndex,
            int logIndex,
            String contractAddress,
            Map<String, Object> payload,
            String canonicalStatus) {
    }

    enum IndexState {
        READY,
        REBUILDING,
        NOT_READY,
        INVALID_NETWORK
    }
}
