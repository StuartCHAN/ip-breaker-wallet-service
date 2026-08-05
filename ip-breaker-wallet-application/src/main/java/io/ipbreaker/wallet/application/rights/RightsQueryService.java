package io.ipbreaker.wallet.application.rights;

import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository.EventCursor;
import io.ipbreaker.wallet.rights.query.RightsQueryRepository;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RightsQueryService {
    private final RightsQueryRepository repository;

    public RightsQueryService(RightsQueryRepository repository) {
        this.repository = repository;
    }

    public RightsQueryRepository.IpAssetView asset(String network, BigInteger assetId) {
        requireReady(network);
        return repository.findAsset(network, assetId)
                .orElseThrow(() -> new RightsNotFoundException(RightsNotFoundException.Resource.IP_ASSET));
    }

    public RightsQueryRepository.LicenseAgreementView agreement(
            String network, BigInteger agreementId) {
        requireReady(network);
        return repository.findAgreement(network, agreementId).orElseThrow(() ->
                new RightsNotFoundException(RightsNotFoundException.Resource.LICENSE_AGREEMENT));
    }

    public TimelinePage timeline(
            String network, BigInteger assetId, String encodedCursor, int limit) {
        requireReady(network);
        List<RightsQueryRepository.TimelineEventView> events = repository.findTimeline(
                network, assetId, decode(encodedCursor), limit + 1);
        boolean hasMore = events.size() > limit;
        List<RightsQueryRepository.TimelineEventView> page = hasMore
                ? events.subList(0, limit) : events;
        String next = hasMore ? encode(page.getLast()) : null;
        return new TimelinePage(List.copyOf(page), next);
    }

    private EventCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid timeline cursor");
            }
            return new EventCursor(Long.parseLong(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid timeline cursor", exception);
        }
    }

    private void requireReady(String network) {
        RightsQueryRepository.IndexState state = repository.indexState(network);
        if (state == RightsQueryRepository.IndexState.INVALID_NETWORK) {
            throw new IllegalArgumentException("Invalid network");
        }
        if (state != RightsQueryRepository.IndexState.READY) {
            throw new RightsIndexUnavailableException(
                    state == RightsQueryRepository.IndexState.REBUILDING);
        }
    }

    private String encode(RightsQueryRepository.TimelineEventView event) {
        String raw = event.blockNumber() + ":" + event.transactionIndex() + ":" + event.logIndex();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public record TimelinePage(
            List<RightsQueryRepository.TimelineEventView> items,
            String nextCursor) {
    }
}
