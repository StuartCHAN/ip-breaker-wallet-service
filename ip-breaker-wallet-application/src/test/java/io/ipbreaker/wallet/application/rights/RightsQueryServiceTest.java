package io.ipbreaker.wallet.application.rights;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository.EventCursor;
import io.ipbreaker.wallet.rights.query.RightsQueryRepository;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RightsQueryServiceTest {
    @Test
    void timelineUsesOpaqueKeysetCursorWithoutDroppingUint256Identifiers() {
        FakeRepository repository = new FakeRepository();
        RightsQueryService service = new RightsQueryService(repository);

        RightsQueryService.TimelinePage first = service.timeline(
                "SEPOLIA", BigInteger.ONE.shiftLeft(200), null, 1);
        RightsQueryService.TimelinePage second = service.timeline(
                "SEPOLIA", BigInteger.ONE.shiftLeft(200), first.nextCursor(), 1);

        assertEquals(1L, first.items().getFirst().eventId());
        assertEquals(2L, second.items().getFirst().eventId());
        assertEquals(new EventCursor(10L, 0, 1), repository.lastCursor);
    }

    @Test
    void rebuildingIsReportedSeparatelyFromNotFound() {
        FakeRepository repository = new FakeRepository();
        repository.state = RightsQueryRepository.IndexState.REBUILDING;
        RightsQueryService service = new RightsQueryService(repository);

        RightsIndexUnavailableException exception = assertThrows(
                RightsIndexUnavailableException.class,
                () -> service.asset("SEPOLIA", BigInteger.ONE));

        assertEquals(true, exception.rebuilding());
    }

    private static final class FakeRepository implements RightsQueryRepository {
        private final List<TimelineEventView> events = List.of(event(1L, 10L, 1), event(2L, 11L, 0));

        private IndexState state = IndexState.READY;

        private EventCursor lastCursor;

        @Override
        public IndexState indexState(String networkCode) {
            return state;
        }

        @Override
        public Optional<IpAssetView> findAsset(String networkCode, BigInteger assetId) {
            return Optional.empty();
        }

        @Override
        public Optional<LicenseAgreementView> findAgreement(
                String networkCode, BigInteger agreementId) {
            return Optional.empty();
        }

        @Override
        public List<TimelineEventView> findTimeline(
                String networkCode, BigInteger assetId, EventCursor cursor, int limit) {
            lastCursor = cursor;
            List<TimelineEventView> result = new ArrayList<>();
            for (TimelineEventView event : events) {
                if (cursor == null || event.blockNumber() > cursor.blockNumber()
                        || (event.blockNumber() == cursor.blockNumber()
                        && event.logIndex() > cursor.logIndex())) {
                    result.add(event);
                }
            }
            return result.subList(0, Math.min(limit, result.size()));
        }

        private static TimelineEventView event(long id, long block, int logIndex) {
            return new TimelineEventView(id, "EVENT", "IP_ASSET", BigInteger.ONE,
                    block, hash(block), Instant.EPOCH, hash(id), 0, logIndex,
                    "0x1111111111111111111111111111111111111111", Map.of(), "CANONICAL");
        }

        private static String hash(long value) {
            return "0x" + String.format("%064x", value);
        }
    }
}
