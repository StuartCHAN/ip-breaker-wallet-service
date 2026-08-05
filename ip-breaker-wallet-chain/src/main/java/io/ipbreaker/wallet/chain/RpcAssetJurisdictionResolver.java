package io.ipbreaker.wallet.chain;

import io.ipbreaker.wallet.rights.event.AssetJurisdictionResolver;
import java.math.BigInteger;
import org.springframework.stereotype.Component;

@Component
public class RpcAssetJurisdictionResolver implements AssetJurisdictionResolver {
    private final BlockchainRpcClient rpcClient;

    public RpcAssetJurisdictionResolver(BlockchainRpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public String resolve(String registryAddress, BigInteger assetId, long blockNumber) {
        return rpcClient.getAssetJurisdiction(registryAddress, assetId, blockNumber);
    }
}
