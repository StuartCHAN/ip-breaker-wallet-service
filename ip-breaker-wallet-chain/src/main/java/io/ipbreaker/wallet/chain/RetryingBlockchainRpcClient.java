package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import java.time.Duration;
import java.util.function.Supplier;

public class RetryingBlockchainRpcClient implements BlockchainRpcClient {
    private final BlockchainRpcClient delegate;

    private final int attempts;

    private final Duration initialBackoff;

    public RetryingBlockchainRpcClient(
            BlockchainRpcClient delegate,
            int attempts,
            Duration initialBackoff) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        this.delegate = delegate;
        this.attempts = attempts;
        this.initialBackoff = initialBackoff;
    }

    @Override
    public long latestBlockNumber() {
        return execute(delegate::latestBlockNumber);
    }

    @Override
    public ScannedBlock getBlock(long blockNumber) {
        return execute(() -> delegate.getBlock(blockNumber));
    }

    private <T> T execute(Supplier<T> action) {
        RpcException lastFailure = null;
        long backoffMillis = initialBackoff.toMillis();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.get();
            } catch (RpcException exception) {
                lastFailure = exception;
                if (attempt < attempts) {
                    sleep(backoffMillis);
                    backoffMillis = Math.multiplyExact(backoffMillis, 2L);
                }
            }
        }
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
