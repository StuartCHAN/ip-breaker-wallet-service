package io.ipbreaker.wallet.application.rights;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.rights.backfill.BackfillCursorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RightsBackfillProcessor {
    private final RightsEventIngestor ingestor;
    private final BackfillCursorRepository cursorRepository;

    public RightsBackfillProcessor(
            RightsEventIngestor ingestor, BackfillCursorRepository cursorRepository) {
        this.ingestor = ingestor;
        this.cursorRepository = cursorRepository;
    }

    @Transactional
    public void process(
            long networkId, long contractId, String owner, ScannedBlock block) {
        ingestor.ingest(networkId, block);
        cursorRepository.advance(networkId, contractId, owner, block.number());
    }
}
