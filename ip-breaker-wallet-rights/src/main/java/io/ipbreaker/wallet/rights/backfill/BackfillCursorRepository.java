package io.ipbreaker.wallet.rights.backfill;

import io.ipbreaker.wallet.rights.contract.ManagedContract;
import java.time.Duration;
import java.util.Optional;

public interface BackfillCursorRepository {
    void initialize(ManagedContract contract, long targetSafeBlock);

    Optional<BackfillCursor> tryAcquire(long networkId, String owner, Duration duration);

    void advance(long networkId, long contractId, String owner, long processedBlock);

    void release(long networkId, long contractId, String owner);

    record BackfillCursor(
            long networkId,
            long contractId,
            long nextBlock,
            long targetSafeBlock) {
    }
}
