package io.ipbreaker.wallet.application.deposit;

import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.rights.RightsEventIngestor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockDepositProcessor {
    private final BlockScanRepository blockRepository;

    private final DepositRepository depositRepository;

    private final DepositDetector detector;

    private final RightsEventIngestor rightsEventIngestor;

    @Autowired
    public BlockDepositProcessor(
            BlockScanRepository blockRepository,
            DepositRepository depositRepository,
            DepositDetector detector,
            RightsEventIngestor rightsEventIngestor) {
        this.blockRepository = blockRepository;
        this.depositRepository = depositRepository;
        this.detector = detector;
        this.rightsEventIngestor = rightsEventIngestor;
    }

    public BlockDepositProcessor(
            BlockScanRepository blockRepository,
            DepositRepository depositRepository,
            DepositDetector detector) {
        this(blockRepository, depositRepository, detector, null);
    }

    @Transactional
    public void process(long networkId, String leaseOwner, ScannedBlock block) {
        blockRepository.saveBlockAndAdvance(networkId, leaseOwner, block);
        if (rightsEventIngestor != null) {
            rightsEventIngestor.ingest(networkId, block);
        }
        for (DepositCandidate candidate : detector.detect(block)) {
            depositRepository.insertMatching(networkId, candidate);
        }
    }
}
