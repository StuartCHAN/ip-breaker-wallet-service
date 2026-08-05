package io.ipbreaker.wallet.job;

import io.ipbreaker.wallet.application.deposit.BlockDepositProcessor;
import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScanCursor;
import io.ipbreaker.wallet.application.scan.ScanNetwork;
import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlockScannerJob {
    private final BlockchainRpcClient rpcClient;

    private final BlockScanRepository repository;

    private final BlockDepositProcessor depositProcessor;

    private final ChainReorganizationService reorganizationService;

    private final String networkCode;

    private final int batchSize;

    private final Duration leaseDuration;

    private final String instanceId = UUID.randomUUID().toString();

    private final AtomicLong scannedHeight = new AtomicLong();

    private final AtomicLong safeHeight = new AtomicLong();

    private final Counter failures;

    public BlockScannerJob(
            BlockchainRpcClient rpcClient,
            BlockScanRepository repository,
            BlockDepositProcessor depositProcessor,
            ChainReorganizationService reorganizationService,
            MeterRegistry meterRegistry,
            @Value("${wallet.scanner.network-code}") String networkCode,
            @Value("${wallet.scanner.batch-size}") int batchSize,
            @Value("${wallet.scanner.lease-duration}") Duration leaseDuration) {
        this.rpcClient = rpcClient;
        this.repository = repository;
        this.depositProcessor = depositProcessor;
        this.reorganizationService = reorganizationService;
        this.networkCode = networkCode;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        Gauge.builder("wallet.scanner.height", scannedHeight, AtomicLong::get)
                .description("Last committed scanner height").register(meterRegistry);
        Gauge.builder("wallet.scanner.safe.height", safeHeight, AtomicLong::get)
                .description("Latest safe chain height").register(meterRegistry);
        Gauge.builder("wallet.scanner.lag", this,
                        job -> Math.max(0L, job.safeHeight.get() - job.scannedHeight.get()))
                .description("Blocks behind the safe chain height").register(meterRegistry);
        this.failures = meterRegistry.counter("wallet.scanner.failures");
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
            long currentSafeHeight = Math.max(0L, rpcClient.latestBlockNumber()
                    - network.requiredConfirmations());
            safeHeight.set(currentSafeHeight);
            scannedHeight.set(cursor.lastScannedBlock());
            long lastHeight = Math.min(currentSafeHeight, cursor.lastScannedBlock() + batchSize);
            for (long height = cursor.lastScannedBlock() + 1L; height <= lastHeight; height++) {
                if (!repository.tryAcquireLease(network.id(), instanceId, leaseDuration)) {
                    throw new IllegalStateException("Scanner lease could not be renewed");
                }
                ScannedBlock block = rpcClient.getBlock(height);
                ScanCursor current = repository.getOrCreateCursor(network);
                if (!current.lastScannedHash().equals(zeroHash())
                        && !block.parentHash().equals(current.lastScannedHash())) {
                    reorganizationService.reconcile(
                            network.id(), instanceId, current, network.startBlock());
                    return;
                }
                depositProcessor.process(network.id(), instanceId, block);
                scannedHeight.set(height);
            }
        } catch (RuntimeException exception) {
            failures.increment();
            throw exception;
        } finally {
            repository.releaseLease(network.id(), instanceId);
        }
    }

    private String zeroHash() {
        return "0x0000000000000000000000000000000000000000000000000000000000000000";
    }
}
