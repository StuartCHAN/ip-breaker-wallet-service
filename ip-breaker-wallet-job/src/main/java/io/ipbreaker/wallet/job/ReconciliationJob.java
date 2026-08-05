package io.ipbreaker.wallet.job;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationJob {
    private final ReconciliationService service;

    private final String networkCode;

    private final Counter differences;

    private final Counter failures;

    public ReconciliationJob(
            ReconciliationService service,
            MeterRegistry meterRegistry,
            @Value("${wallet.scanner.network-code}") String networkCode) {
        this.service = service;
        this.networkCode = networkCode;
        this.differences = meterRegistry.counter("wallet.reconciliation.differences");
        this.failures = meterRegistry.counter("wallet.reconciliation.failures");
    }

    @Scheduled(fixedDelayString = "${wallet.reconciliation.fixed-delay}")
    public void reconcile() {
        try {
            int count = service.reconcileLedger(networkCode)
                    + service.reconcileDeposits(networkCode)
                    + service.reconcileOnChain(networkCode);
            differences.increment(count);
        } catch (RuntimeException exception) {
            failures.increment();
            throw exception;
        }
    }
}
