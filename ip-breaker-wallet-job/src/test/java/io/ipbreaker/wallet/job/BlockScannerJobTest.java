package io.ipbreaker.wallet.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ipbreaker.wallet.application.deposit.BlockDepositProcessor;
import io.ipbreaker.wallet.application.deposit.DepositCandidate;
import io.ipbreaker.wallet.application.deposit.DepositDetector;
import io.ipbreaker.wallet.application.deposit.DepositRepository;
import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScanCursor;
import io.ipbreaker.wallet.application.scan.ScanNetwork;
import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import io.ipbreaker.wallet.domain.deposit.Deposit;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlockScannerJobTest {
    @Test
    void scansOnlyThroughSafeHeightAndAdvancesInOrder() {
        FakeRepository repository = new FakeRepository();
        BlockchainRpcClient client = new BlockchainRpcClient() {
            @Override
            public long latestBlockNumber() {
                return 110L;
            }

            @Override
            public ScannedBlock getBlock(long blockNumber) {
                return new ScannedBlock(
                        blockNumber,
                        hash(blockNumber),
                        hash(blockNumber - 1L),
                        Instant.EPOCH,
                        List.of());
            }
        };
        BlockScannerJob job = new BlockScannerJob(
                client,
                repository,
                processor(repository),
                "sepolia",
                25,
                Duration.ofSeconds(30));

        job.scan();

        assertEquals(List.of(101L, 102L, 103L, 104L), repository.savedHeights);
        assertEquals(104L, repository.cursor.lastScannedBlock());
    }

    @Test
    void doesNothingWhenAnotherInstanceHoldsLease() {
        FakeRepository repository = new FakeRepository();
        repository.leaseAvailable = false;
        BlockchainRpcClient client = new BlockchainRpcClient() {
            @Override
            public long latestBlockNumber() {
                throw new AssertionError("RPC must not be called without the lease");
            }

            @Override
            public ScannedBlock getBlock(long blockNumber) {
                throw new AssertionError("RPC must not be called without the lease");
            }
        };
        BlockScannerJob job = new BlockScannerJob(
                client,
                repository,
                processor(repository),
                "SEPOLIA",
                25,
                Duration.ofSeconds(30));

        job.scan();

        assertEquals(List.of(), repository.savedHeights);
    }

    private static String hash(long height) {
        return "0x" + String.format("%064x", height);
    }

    private static BlockDepositProcessor processor(BlockScanRepository repository) {
        DepositRepository deposits = new DepositRepository() {
            @Override
            public void insertMatching(long networkId, DepositCandidate candidate) {
            }

            @Override
            public List<Deposit> findByUserId(String userId) {
                return List.of();
            }

            @Override
            public Optional<Deposit> findById(long depositId) {
                return Optional.empty();
            }
        };
        return new BlockDepositProcessor(repository, deposits, new DepositDetector());
    }

    private static final class FakeRepository implements BlockScanRepository {
        private final List<Long> savedHeights = new ArrayList<>();

        private ScanCursor cursor = new ScanCursor(100L, hash(100L));

        private boolean leaseAvailable = true;

        @Override
        public Optional<ScanNetwork> findEnabledNetwork(String networkCode) {
            return Optional.of(new ScanNetwork(1L, networkCode, 6, 100L));
        }

        @Override
        public ScanCursor getOrCreateCursor(ScanNetwork network) {
            return cursor;
        }

        @Override
        public boolean tryAcquireLease(long networkId, String owner, Duration duration) {
            return leaseAvailable;
        }

        @Override
        public void saveBlockAndAdvance(long networkId, String owner, ScannedBlock block) {
            savedHeights.add(block.number());
            cursor = new ScanCursor(block.number(), block.hash());
        }

        @Override
        public void releaseLease(long networkId, String owner) {
        }
    }
}
