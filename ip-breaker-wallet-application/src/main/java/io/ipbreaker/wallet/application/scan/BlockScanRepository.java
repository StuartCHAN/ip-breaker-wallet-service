package io.ipbreaker.wallet.application.scan;

import java.time.Duration;
import java.util.Optional;

public interface BlockScanRepository {
    Optional<ScanNetwork> findEnabledNetwork(String networkCode);

    ScanCursor getOrCreateCursor(ScanNetwork network);

    boolean tryAcquireLease(long networkId, String owner, Duration duration);

    void saveBlockAndAdvance(long networkId, String owner, ScannedBlock block);

    Optional<String> findCanonicalBlockHash(long networkId, long blockNumber);

    void rollbackToAncestor(long networkId, String owner, long blockNumber, String blockHash);

    void releaseLease(long networkId, String owner);
}
