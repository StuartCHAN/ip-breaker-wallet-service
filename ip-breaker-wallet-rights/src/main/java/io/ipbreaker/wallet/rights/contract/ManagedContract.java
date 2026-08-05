package io.ipbreaker.wallet.rights.contract;

public record ManagedContract(
        long id,
        long networkId,
        ContractType type,
        String address,
        String abiVersion,
        long deploymentBlock,
        String runtimeCodeHash) {
}
