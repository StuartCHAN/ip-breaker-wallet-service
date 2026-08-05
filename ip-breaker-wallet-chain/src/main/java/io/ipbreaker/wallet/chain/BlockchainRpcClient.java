package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.application.scan.ScannedBlock;

public interface BlockchainRpcClient {
    long latestBlockNumber();

    ScannedBlock getBlock(long blockNumber);
}
