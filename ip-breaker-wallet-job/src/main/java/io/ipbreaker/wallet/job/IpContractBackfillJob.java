package io.ipbreaker.wallet.job;

import io.ipbreaker.wallet.application.rights.RightsBackfillProcessor;
import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import io.ipbreaker.wallet.rights.backfill.BackfillCursorRepository;
import io.ipbreaker.wallet.rights.contract.ManagedContractRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IpContractBackfillJob {
    private final BlockchainRpcClient rpcClient;
    private final BlockScanRepository scanRepository;
    private final ManagedContractRepository contractRepository;
    private final BackfillCursorRepository cursorRepository;
    private final RightsBackfillProcessor processor;
    private final String networkCode;
    private final int batchSize;
    private final Duration leaseDuration;
    private final String owner = UUID.randomUUID().toString();

    public IpContractBackfillJob(
            BlockchainRpcClient rpcClient,
            BlockScanRepository scanRepository,
            ManagedContractRepository contractRepository,
            BackfillCursorRepository cursorRepository,
            RightsBackfillProcessor processor,
            @Value("${wallet.scanner.network-code}") String networkCode,
            @Value("${wallet.rights.backfill.batch-size:10}") int batchSize,
            @Value("${wallet.rights.backfill.lease-duration:45s}") Duration leaseDuration) {
        this.rpcClient = rpcClient;
        this.scanRepository = scanRepository;
        this.contractRepository = contractRepository;
        this.cursorRepository = cursorRepository;
        this.processor = processor;
        this.networkCode = networkCode;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
    }

    @Scheduled(fixedDelayString = "${wallet.rights.backfill.fixed-delay:15s}")
    public void backfill() {
        var network = scanRepository.findEnabledNetwork(networkCode).orElse(null);
        if (network == null) {
            return;
        }
        var contracts = contractRepository.findActive(network.id());
        if (contracts.isEmpty()) {
            return;
        }
        long safeHeight = Math.max(0L,
                rpcClient.latestBlockNumber() - network.requiredConfirmations());
        long walletHeight = scanRepository.getOrCreateCursor(network).lastScannedBlock();
        long target = Math.min(safeHeight, walletHeight);
        contracts.forEach(contract -> cursorRepository.initialize(contract, target));
        var cursor = cursorRepository.tryAcquire(network.id(), owner, leaseDuration).orElse(null);
        if (cursor == null) {
            return;
        }
        try {
            long last = Math.min(cursor.targetSafeBlock(), cursor.nextBlock() + batchSize - 1L);
            for (long height = cursor.nextBlock(); height <= last; height++) {
                processor.process(network.id(), cursor.contractId(), owner, rpcClient.getBlock(height));
            }
        } finally {
            cursorRepository.release(network.id(), cursor.contractId(), owner);
        }
    }
}
