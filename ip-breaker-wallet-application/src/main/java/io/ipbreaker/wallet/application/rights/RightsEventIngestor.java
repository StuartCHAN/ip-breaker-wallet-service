package io.ipbreaker.wallet.application.rights;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.settlement.SettlementEligibilityService;
import io.ipbreaker.wallet.application.settlement.SettlementLedgerService;
import io.ipbreaker.wallet.rights.contract.ManagedContract;
import io.ipbreaker.wallet.rights.contract.ManagedContractRepository;
import io.ipbreaker.wallet.rights.event.AssetJurisdictionResolver;
import io.ipbreaker.wallet.rights.event.ChainDomainEventRepository;
import io.ipbreaker.wallet.rights.event.ContractEventDecoder;
import io.ipbreaker.wallet.rights.event.LogEnvelope;
import io.ipbreaker.wallet.rights.event.KnownEventDecodingException;
import io.ipbreaker.wallet.rights.projection.RightsProjectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RightsEventIngestor {
    private static final Comparator<LogEnvelope> LOG_ORDER = Comparator
            .comparingLong(LogEnvelope::blockNumber)
            .thenComparingInt(LogEnvelope::transactionIndex)
            .thenComparingInt(LogEnvelope::logIndex);

    private final ManagedContractRepository contractRepository;
    private final ChainDomainEventRepository eventRepository;
    private final RightsProjectionRepository projectionRepository;

    private final SettlementEligibilityService eligibilityService;
    private final SettlementLedgerService settlementLedgerService;

    private final MeterRegistry meterRegistry;
    private final ContractEventDecoder decoder;

    public RightsEventIngestor(
            ManagedContractRepository contractRepository,
            ChainDomainEventRepository eventRepository,
            RightsProjectionRepository projectionRepository,
            SettlementEligibilityService eligibilityService,
            SettlementLedgerService settlementLedgerService,
            MeterRegistry meterRegistry,
            AssetJurisdictionResolver jurisdictionResolver) {
        this.contractRepository = contractRepository;
        this.eventRepository = eventRepository;
        this.projectionRepository = projectionRepository;
        this.eligibilityService = eligibilityService;
        this.settlementLedgerService = settlementLedgerService;
        this.meterRegistry = meterRegistry;
        this.decoder = new ContractEventDecoder(jurisdictionResolver);
    }

    public void ingest(long networkId, ScannedBlock block) {
        Map<String, ManagedContract> contracts = new HashMap<>();
        for (ManagedContract contract : contractRepository.findActive(networkId)) {
            contracts.put(contract.address().toLowerCase(Locale.ROOT), contract);
        }
        if (contracts.isEmpty()) {
            return;
        }
        List<LogEnvelope> logs = trackedLogs(networkId, block, contracts);
        validateOrder(logs);
        for (LogEnvelope log : logs) {
            ManagedContract contract = contracts.get(log.contractAddress().toLowerCase(Locale.ROOT));
            io.ipbreaker.wallet.rights.event.DecodedContractEvent decoded;
            try {
                decoded = decoder.decode(contract, log);
            } catch (KnownEventDecodingException exception) {
                meterRegistry.counter("wallet.rights.decode.failures", "contract",
                        contract.type().name()).increment();
                throw exception;
            }
            ChainDomainEventRepository.PersistedEvent saved = eventRepository.save(decoded);
            meterRegistry.counter("wallet.rights.events.total", "contract", contract.type().name(),
                    "event_type", decoded.eventType().name()).increment();
            if (saved.unknown()) {
                meterRegistry.counter("wallet.rights.events.unknown.total", "contract",
                        contract.type().name(), "topic0", log.topics().getFirst()).increment();
            }
            if (!saved.unknown() && saved.newlyCanonicalized()) {
                try {
                    projectionRepository.apply(saved.event());
                    eligibilityService.onCanonicalEvent(saved.event());
                    settlementLedgerService.onCanonicalEvent(saved.event());
                } catch (RuntimeException exception) {
                    meterRegistry.counter("wallet.rights.projection.failures", "projection_type",
                            saved.event().aggregateType() == null ? "NONE"
                                    : saved.event().aggregateType().name()).increment();
                    throw exception;
                }
            }
        }
    }

    private List<LogEnvelope> trackedLogs(
            long networkId, ScannedBlock block, Map<String, ManagedContract> contracts) {
        List<LogEnvelope> result = new ArrayList<>();
        block.transactions().stream()
                .filter(transaction -> transaction.receipt().success())
                .forEach(transaction -> transaction.receipt().logs().stream()
                        .filter(log -> contracts.containsKey(log.contractAddress().toLowerCase(Locale.ROOT)))
                        .forEach(log -> result.add(new LogEnvelope(
                                networkId,
                                block.hash(),
                                block.number(),
                                block.timestamp(),
                                transaction.hash(),
                                transaction.transactionIndex(),
                                transaction.inputData(),
                                log.contractAddress().toLowerCase(Locale.ROOT),
                                log.logIndex(),
                                log.topics(),
                                log.data()))));
        result.sort(LOG_ORDER);
        return result;
    }

    private void validateOrder(List<LogEnvelope> logs) {
        int previousLogIndex = -1;
        for (LogEnvelope log : logs) {
            if (log.logIndex() < 0 || log.logIndex() <= previousLogIndex) {
                throw new IllegalStateException("Managed contract logs are duplicated or out of order");
            }
            previousLogIndex = log.logIndex();
        }
    }
}
