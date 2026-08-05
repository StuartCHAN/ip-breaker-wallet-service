package io.ipbreaker.wallet.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ipbreaker.wallet.application.reconciliation.OnChainBalanceTarget;
import io.ipbreaker.wallet.application.reconciliation.ReconciliationDifference;
import io.ipbreaker.wallet.application.reconciliation.ReconciliationRepository;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {
    @Test
    void aggregatesAddressesAndPersistsOneAssetDifference() {
        FakeRepository repository = new FakeRepository();
        BlockchainRpcClient rpcClient = new BlockchainRpcClient() {
            @Override
            public long latestBlockNumber() {
                return 0L;
            }

            @Override
            public io.ipbreaker.wallet.application.scan.ScannedBlock getBlock(long blockNumber) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BigInteger getNativeBalance(String address, long blockNumber) {
                return BigInteger.valueOf(40L);
            }
        };
        ReconciliationService service = new ReconciliationService(repository, rpcClient);

        assertEquals(1, service.reconcileOnChain("SEPOLIA"));
        assertEquals(BigInteger.valueOf(100L), repository.saved.getFirst().expectedAmount());
        assertEquals(BigInteger.valueOf(80L), repository.saved.getFirst().actualAmount());
    }

    private static final class FakeRepository implements ReconciliationRepository {
        private List<ReconciliationDifference> saved = new ArrayList<>();

        @Override
        public List<ReconciliationDifference> findLedgerBalanceDifferences() {
            return List.of();
        }

        @Override
        public List<ReconciliationDifference> findDepositLedgerDifferences() {
            return List.of();
        }

        @Override
        public List<OnChainBalanceTarget> findOnChainBalanceTargets(String networkCode) {
            return List.of(
                    new OnChainBalanceTarget(networkCode, "ETH", "NATIVE", null, "0x1", 100L),
                    new OnChainBalanceTarget(networkCode, "ETH", "NATIVE", null, "0x2", 100L));
        }

        @Override
        public BigInteger findPlatformLedgerBalance(String networkCode, String assetCode) {
            return BigInteger.valueOf(100L);
        }

        @Override
        public void replaceResults(
                String checkType, List<ReconciliationDifference> differences) {
            saved = new ArrayList<>(differences);
        }
    }
}
