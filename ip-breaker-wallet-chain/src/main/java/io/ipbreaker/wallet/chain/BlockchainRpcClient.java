package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import java.math.BigInteger;

public interface BlockchainRpcClient {
    long latestBlockNumber();

    ScannedBlock getBlock(long blockNumber);

    default BigInteger getNativeBalance(String address, long blockNumber) {
        throw new UnsupportedOperationException("Native balance lookup is not implemented");
    }

    default BigInteger getTokenBalance(String contractAddress, String address, long blockNumber) {
        throw new UnsupportedOperationException("Token balance lookup is not implemented");
    }
}
