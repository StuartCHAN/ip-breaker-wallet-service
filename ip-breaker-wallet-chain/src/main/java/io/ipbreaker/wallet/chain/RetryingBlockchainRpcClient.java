package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigInteger;
import java.time.Duration;
import java.util.function.Supplier;

public class RetryingBlockchainRpcClient implements BlockchainRpcClient {
    private final BlockchainRpcClient delegate;

    private final int attempts;

    private final Duration initialBackoff;

    private final MeterRegistry meterRegistry;

    private final Counter failures;

    public RetryingBlockchainRpcClient(
            BlockchainRpcClient delegate,
            int attempts,
            Duration initialBackoff) {
        this(delegate, attempts, initialBackoff, new SimpleMeterRegistry());
    }

    public RetryingBlockchainRpcClient(
            BlockchainRpcClient delegate,
            int attempts,
            Duration initialBackoff,
            MeterRegistry meterRegistry) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        this.delegate = delegate;
        this.attempts = attempts;
        this.initialBackoff = initialBackoff;
        this.meterRegistry = meterRegistry;
        this.failures = meterRegistry.counter("wallet.rpc.failures");
    }

    @Override
    public long latestBlockNumber() {
        return execute(delegate::latestBlockNumber);
    }

    @Override
    public ScannedBlock getBlock(long blockNumber) {
        return execute(() -> delegate.getBlock(blockNumber));
    }

    @Override
    public BigInteger getNativeBalance(String address, long blockNumber) {
        return execute(() -> delegate.getNativeBalance(address, blockNumber));
    }

    @Override
    public BigInteger getTokenBalance(String contractAddress, String address, long blockNumber) {
        return execute(() -> delegate.getTokenBalance(contractAddress, address, blockNumber));
    }

    private <T> T execute(Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        RpcException lastFailure = null;
        long backoffMillis = initialBackoff.toMillis();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = action.get();
                sample.stop(meterRegistry.timer("wallet.rpc.latency"));
                return result;
            } catch (RpcException exception) {
                failures.increment();
                lastFailure = exception;
                if (attempt < attempts) {
                    sleep(backoffMillis);
                    backoffMillis = Math.multiplyExact(backoffMillis, 2L);
                }
            }
        }
        sample.stop(meterRegistry.timer("wallet.rpc.latency"));
        throw lastFailure;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RpcException("RPC retry interrupted", exception);
        }
    }
}
