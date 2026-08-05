package io.ipbreaker.wallet.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RetryingBlockchainRpcClient client = new RetryingBlockchainRpcClient(
                delegate, 3, Duration.ZERO, registry);

        assertEquals(100L, client.latestBlockNumber());
        assertEquals(3, calls.get());
        assertEquals(2.0, registry.counter("wallet.rpc.failures").count());
        assertEquals(1L, registry.timer("wallet.rpc.latency").count());
    }
}
