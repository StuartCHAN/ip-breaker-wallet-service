package io.ipbreaker.wallet.application.scan;

import java.time.Duration;
import java.util.Optional;

public interface BlockScanRepository {
    Optional<ScanNetwork> findEnabledNetwork(String networkCode);

    ScanCursor getOrCreateCursor(ScanNetwork network);

    boolean tryAcquireLease(long networkId, String owner, Duration duration);

    void saveBlockAndAdvance(long networkId, String owner, ScannedBlock block);

    void releaseLease(long networkId, String owner);
}
