package io.ipbreaker.wallet.application.deposit;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.scan.ScannedLog;
import io.ipbreaker.wallet.application.scan.ScannedTransaction;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DepositDetector {
    static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final int NATIVE_LOG_INDEX = -1;

    public List<DepositCandidate> detect(ScannedBlock block) {
        List<DepositCandidate> candidates = new ArrayList<>();
        for (ScannedTransaction transaction : block.transactions()) {
            if (!transaction.receipt().success()) {
                continue;
            }
            detectNative(block, transaction, candidates);
            detectTokens(block, transaction, candidates);
        }
        return List.copyOf(candidates);
    }

    private void detectNative(
            ScannedBlock block,
            ScannedTransaction transaction,
            List<DepositCandidate> candidates) {
        if (transaction.toAddress() == null || transaction.value().signum() <= 0) {
            return;
        }
        candidates.add(new DepositCandidate(
                "NATIVE",
                null,
                normalize(transaction.hash()),
                NATIVE_LOG_INDEX,
                normalize(transaction.fromAddress()),
                normalize(transaction.toAddress()),
                transaction.value(),
                block.number()));
    }

    private void detectTokens(
            ScannedBlock block,
            ScannedTransaction transaction,
            List<DepositCandidate> candidates) {
        for (ScannedLog log : transaction.receipt().logs()) {
            toTokenCandidate(block, transaction, log).ifPresent(candidates::add);
        }
    }

    private java.util.Optional<DepositCandidate> toTokenCandidate(
            ScannedBlock block, ScannedTransaction transaction, ScannedLog log) {
        if (log.topics().size() < 3
                || !TRANSFER_TOPIC.equalsIgnoreCase(log.topics().getFirst())
                || !isWord(log.topics().get(1))
                || !isWord(log.topics().get(2))
                || !isWord(log.data())) {
            return java.util.Optional.empty();
        }
        BigInteger amount = new BigInteger(log.data().substring(2), 16);
        if (amount.signum() <= 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new DepositCandidate(
                "ERC20",
                normalize(log.contractAddress()),
                normalize(transaction.hash()),
                log.logIndex(),
                topicAddress(log.topics().get(1)),
                topicAddress(log.topics().get(2)),
                amount,
                block.number()));
    }

    private boolean isWord(String value) {
        return value != null && value.matches("0x[0-9a-fA-F]{64}");
    }

    private String topicAddress(String topic) {
        return "0x" + topic.substring(topic.length() - 40).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
