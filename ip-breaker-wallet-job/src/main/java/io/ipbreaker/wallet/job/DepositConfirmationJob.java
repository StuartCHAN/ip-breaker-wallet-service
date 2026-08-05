package io.ipbreaker.wallet.job;

import io.ipbreaker.wallet.application.ledger.DepositConfirmationService;
import io.ipbreaker.wallet.application.ledger.DepositCreditService;
import io.ipbreaker.wallet.application.ledger.LedgerRepository;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DepositConfirmationJob {
    private final BlockchainRpcClient rpcClient;

    private final LedgerRepository repository;

    private final DepositConfirmationService confirmationService;

    private final DepositCreditService creditService;

    private final String networkCode;

    private final int batchSize;

    public DepositConfirmationJob(
            BlockchainRpcClient rpcClient,
            LedgerRepository repository,
            DepositConfirmationService confirmationService,
            DepositCreditService creditService,
            @Value("${wallet.scanner.network-code}") String networkCode,
            @Value("${wallet.confirmation.batch-size}") int batchSize) {
        this.rpcClient = rpcClient;
        this.repository = repository;
        this.confirmationService = confirmationService;
        this.creditService = creditService;
        this.networkCode = networkCode;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${wallet.confirmation.fixed-delay}")
    public void confirmAndCredit() {
        long latestBlockNumber = rpcClient.latestBlockNumber();
        confirmationService.update(networkCode, latestBlockNumber);
        for (long depositId : repository.findConfirmedDepositIds(networkCode, batchSize)) {
            creditService.credit(depositId);
        }
    }
}
