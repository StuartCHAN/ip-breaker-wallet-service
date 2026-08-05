package io.ipbreaker.wallet;

import io.ipbreaker.wallet.application.scan.BlockScanRepository;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import io.ipbreaker.wallet.rights.contract.ManagedContractRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

@Component
@ConditionalOnProperty(
        name = "wallet.rights.verify-contract-code",
        havingValue = "true",
        matchIfMissing = true)
public class RightsContractCodeVerifier implements ApplicationRunner {
    private final BlockchainRpcClient rpcClient;
    private final BlockScanRepository scanRepository;
    private final ManagedContractRepository contractRepository;
    private final String networkCode;

    public RightsContractCodeVerifier(
            BlockchainRpcClient rpcClient,
            BlockScanRepository scanRepository,
            ManagedContractRepository contractRepository,
            @Value("${wallet.scanner.network-code}") String networkCode) {
        this.rpcClient = rpcClient;
        this.scanRepository = scanRepository;
        this.contractRepository = contractRepository;
        this.networkCode = networkCode;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var network = scanRepository.findEnabledNetwork(networkCode).orElse(null);
        if (network == null) {
            return;
        }
        var contracts = contractRepository.findActive(network.id());
        if (contracts.isEmpty()) {
            return;
        }
        long safeHeight = Math.max(0L,
                rpcClient.latestBlockNumber() - network.requiredConfirmations());
        contracts.forEach(contract -> {
            String code = rpcClient.getRuntimeCode(contract.address(), safeHeight);
            if ("0x".equals(code)) {
                throw new IllegalStateException("No runtime code at active contract " + contract.address());
            }
            String actualHash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(code)))
                    .toLowerCase(Locale.ROOT);
            if (!actualHash.equals(contract.runtimeCodeHash().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Runtime code hash mismatch for " + contract.address());
            }
        });
    }
}
