package io.ipbreaker.wallet.application.deposit;

import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScannedBlock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockDepositProcessor {
    private final BlockScanRepository blockRepository;

    private final DepositRepository depositRepository;

    private final DepositDetector detector;

    public BlockDepositProcessor(
            BlockScanRepository blockRepository,
            DepositRepository depositRepository,
            DepositDetector detector) {
        this.blockRepository = blockRepository;
        this.depositRepository = depositRepository;
        this.detector = detector;
    }

    @Transactional
    public void process(long networkId, String leaseOwner, ScannedBlock block) {
        blockRepository.saveBlockAndAdvance(networkId, leaseOwner, block);
        for (DepositCandidate candidate : detector.detect(block)) {
            depositRepository.insertMatching(networkId, candidate);
        }
    }
}
