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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
                new ChainReorganizationService(client, repository),
                new SimpleMeterRegistry(),
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
                new ChainReorganizationService(client, repository),
                new SimpleMeterRegistry(),
                "SEPOLIA",
                25,
                Duration.ofSeconds(30));

        job.scan();

        assertEquals(List.of(), repository.savedHeights);
    }

    @Test
    void findsCommonAncestorRollsBackAndRescansReplacementBranch() {
        FakeRepository repository = new FakeRepository();
        repository.cursor = new ScanCursor(103L, oldHash(103L));
        repository.canonicalHashes.put(100L, oldHash(100L));
        repository.canonicalHashes.put(101L, oldHash(101L));
        repository.canonicalHashes.put(102L, oldHash(102L));
        repository.canonicalHashes.put(103L, oldHash(103L));
        BlockchainRpcClient client = new BlockchainRpcClient() {
            @Override
            public long latestBlockNumber() {
                return 110L;
            }

            @Override
            public ScannedBlock getBlock(long blockNumber) {
                boolean replacement = blockNumber >= 102L;
                String blockHash = replacement ? newHash(blockNumber) : oldHash(blockNumber);
                String parentHash = blockNumber == 102L
                        ? oldHash(101L)
                        : replacement ? newHash(blockNumber - 1L) : oldHash(blockNumber - 1L);
                return new ScannedBlock(
                        blockNumber, blockHash, parentHash, Instant.EPOCH, List.of());
            }
        };
        BlockScannerJob job = new BlockScannerJob(
                client,
                repository,
                processor(repository),
                new ChainReorganizationService(client, repository),
                new SimpleMeterRegistry(),
                "SEPOLIA",
                25,
                Duration.ofSeconds(30));

        job.scan();
        job.scan();

        assertEquals(101L, repository.rollbackHeight);
        assertEquals(List.of(102L, 103L, 104L), repository.savedHeights);
        assertEquals(newHash(104L), repository.cursor.lastScannedHash());
    }

    private static String hash(long height) {
        return "0x" + String.format("%064x", height);
    }

    private static String oldHash(long height) {
        return "0x" + String.format("%064x", height);
    }

    private static String newHash(long height) {
        return "0x" + String.format("%064x", height + 1_000_000L);
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

        private final Map<Long, String> canonicalHashes = new HashMap<>();

        private ScanCursor cursor = new ScanCursor(100L, hash(100L));

        private boolean leaseAvailable = true;

        private long rollbackHeight = -1L;

        private FakeRepository() {
            canonicalHashes.put(100L, hash(100L));
        }

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
            canonicalHashes.put(block.number(), block.hash());
            cursor = new ScanCursor(block.number(), block.hash());
        }

        @Override
        public Optional<String> findCanonicalBlockHash(long networkId, long blockNumber) {
            return Optional.ofNullable(canonicalHashes.get(blockNumber));
        }

        @Override
        public void rollbackToAncestor(
                long networkId, String owner, long blockNumber, String blockHash) {
            cursor = new ScanCursor(blockNumber, blockHash);
            rollbackHeight = blockNumber;
            canonicalHashes.keySet().removeIf(height -> height > blockNumber);
        }

        @Override
        public void releaseLease(long networkId, String owner) {
        }
    }
}
