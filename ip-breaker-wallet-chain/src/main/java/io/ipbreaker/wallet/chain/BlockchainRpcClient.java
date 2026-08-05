package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import java.math.BigInteger;

public interface BlockchainRpcClient {
    long latestBlockNumber();

    ScannedBlock getBlock(long blockNumber);

    default String getRuntimeCode(String address, long blockNumber) {
        throw new UnsupportedOperationException("Runtime code lookup is not implemented");
    }

    default String getAssetJurisdiction(
            String registryAddress, BigInteger assetId, long blockNumber) {
        throw new UnsupportedOperationException("Asset lookup is not implemented");
    }

    default BigInteger getNativeBalance(String address, long blockNumber) {
        throw new UnsupportedOperationException("Native balance lookup is not implemented");
    }

    default BigInteger getTokenBalance(String contractAddress, String address, long blockNumber) {
        throw new UnsupportedOperationException("Token balance lookup is not implemented");
    }
}
