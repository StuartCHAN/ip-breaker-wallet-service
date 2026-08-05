package io.ipbreaker.wallet.job;

import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScanCursor;
import io.ipbreaker.wallet.application.scan.ScanNetwork;
import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlockScannerJob {
    private final BlockchainRpcClient rpcClient;

    private final BlockScanRepository repository;

    private final String networkCode;

    private final int batchSize;

    private final Duration leaseDuration;

    private final String instanceId = UUID.randomUUID().toString();

    public BlockScannerJob(
            BlockchainRpcClient rpcClient,
            BlockScanRepository repository,
            @Value("${wallet.scanner.network-code}") String networkCode,
            @Value("${wallet.scanner.batch-size}") int batchSize,
            @Value("${wallet.scanner.lease-duration}") Duration leaseDuration) {
        this.rpcClient = rpcClient;
        this.repository = repository;
        this.networkCode = networkCode;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
    }

    @Scheduled(fixedDelayString = "${wallet.scanner.fixed-delay}")
    public void scan() {
        ScanNetwork network = repository.findEnabledNetwork(networkCode).orElse(null);
        if (network == null) {
            return;
        }
        repository.getOrCreateCursor(network);
        if (!repository.tryAcquireLease(network.id(), instanceId, leaseDuration)) {
            return;
        }
        try {
            ScanCursor cursor = repository.getOrCreateCursor(network);
            long safeHeight = Math.max(0L, rpcClient.latestBlockNumber()
                    - network.requiredConfirmations());
            long lastHeight = Math.min(safeHeight, cursor.lastScannedBlock() + batchSize);
            for (long height = cursor.lastScannedBlock() + 1L; height <= lastHeight; height++) {
                if (!repository.tryAcquireLease(network.id(), instanceId, leaseDuration)) {
                    throw new IllegalStateException("Scanner lease could not be renewed");
                }
                ScannedBlock block = rpcClient.getBlock(height);
                repository.saveBlockAndAdvance(network.id(), instanceId, block);
            }
        } finally {
            repository.releaseLease(network.id(), instanceId);
        }
    }
}
