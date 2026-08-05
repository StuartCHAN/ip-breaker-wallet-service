package io.ipbreaker.wallet.job;

import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.application.scan.ScanCursor;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import org.springframework.stereotype.Service;

@Service
public class ChainReorganizationService {
    private final BlockchainRpcClient rpcClient;

    private final BlockScanRepository repository;

    public ChainReorganizationService(
            BlockchainRpcClient rpcClient, BlockScanRepository repository) {
        this.rpcClient = rpcClient;
        this.repository = repository;
    }

    public long reconcile(long networkId, String leaseOwner, ScanCursor cursor, long startBlock) {
        for (long height = cursor.lastScannedBlock(); height >= startBlock; height--) {
            long candidateHeight = height;
            String storedHash = repository.findCanonicalBlockHash(networkId, candidateHeight)
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing stored block while finding common ancestor at " + candidateHeight));
            String canonicalHash = rpcClient.getBlock(candidateHeight).hash();
            if (storedHash.equals(canonicalHash)) {
                repository.rollbackToAncestor(
                        networkId, leaseOwner, candidateHeight, storedHash);
                return candidateHeight;
            }
        }
        throw new IllegalStateException("No common ancestor found within the scanned range");
    }
}
