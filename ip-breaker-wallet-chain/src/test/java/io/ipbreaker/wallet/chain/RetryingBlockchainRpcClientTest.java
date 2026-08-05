package io.ipbreaker.wallet.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryingBlockchainRpcClientTest {
    @Test
    void retriesTransientRpcFailure() {
        AtomicInteger calls = new AtomicInteger();
        BlockchainRpcClient delegate = new BlockchainRpcClient() {
            @Override
            public long latestBlockNumber() {
                if (calls.incrementAndGet() < 3) {
                    throw new RpcException("temporary");
                }
                return 100L;
            }

            @Override
            public ScannedBlock getBlock(long blockNumber) {
                throw new UnsupportedOperationException();
            }
        };
        RetryingBlockchainRpcClient client = new RetryingBlockchainRpcClient(
                delegate, 3, Duration.ZERO);

        assertEquals(100L, client.latestBlockNumber());
        assertEquals(3, calls.get());
    }
}
