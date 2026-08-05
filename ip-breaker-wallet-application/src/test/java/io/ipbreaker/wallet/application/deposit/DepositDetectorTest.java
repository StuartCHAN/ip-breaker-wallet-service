package io.ipbreaker.wallet.application.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ipbreaker.wallet.application.scan.ScannedBlock;
import io.ipbreaker.wallet.application.scan.ScannedLog;
import io.ipbreaker.wallet.application.scan.ScannedReceipt;
import io.ipbreaker.wallet.application.scan.ScannedTransaction;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepositDetectorTest {
    private static final String TX_HASH = "0x" + "1".repeat(64);

    private static final String SENDER = "0x" + "a".repeat(40);

    private static final String RECIPIENT = "0x" + "b".repeat(40);

    private static final String TOKEN = "0x" + "c".repeat(40);

    private final DepositDetector detector = new DepositDetector();

    @Test
    void distinguishesMultipleTransferLogsInTheSameTransaction() {
        ScannedLog first = transferLog(7, BigInteger.valueOf(100));
        ScannedLog second = transferLog(9, BigInteger.valueOf(250));
        ScannedReceipt receipt = new ScannedReceipt(
                TX_HASH, 123L, 0, true, BigInteger.ONE, null, List.of(first, second));
        ScannedTransaction transaction = new ScannedTransaction(
                TX_HASH, SENDER, TOKEN, BigInteger.ZERO, BigInteger.ZERO, 0, receipt);
        ScannedBlock block = new ScannedBlock(
                123L, "block", "parent", Instant.EPOCH, List.of(transaction));

        List<DepositCandidate> candidates = detector.detect(block);

        assertEquals(2, candidates.size());
        assertEquals(List.of(7, 9), candidates.stream().map(DepositCandidate::logIndex).toList());
        assertEquals(List.of(BigInteger.valueOf(100), BigInteger.valueOf(250)),
                candidates.stream().map(DepositCandidate::amountRaw).toList());
        assertEquals(List.of(RECIPIENT, RECIPIENT),
                candidates.stream().map(DepositCandidate::toAddress).toList());
    }

    @Test
    void detectsSuccessfulNativeTransfer() {
        ScannedReceipt receipt = new ScannedReceipt(
                TX_HASH, 123L, 0, true, BigInteger.ONE, null, List.of());
        ScannedTransaction transaction = new ScannedTransaction(
                TX_HASH, SENDER, RECIPIENT, BigInteger.ZERO, BigInteger.TEN, 0, receipt);
        ScannedBlock block = new ScannedBlock(
                123L, "block", "parent", Instant.EPOCH, List.of(transaction));

        List<DepositCandidate> candidates = detector.detect(block);

        assertEquals(1, candidates.size());
        assertEquals("NATIVE", candidates.getFirst().assetType());
        assertEquals(-1, candidates.getFirst().logIndex());
        assertEquals(BigInteger.TEN, candidates.getFirst().amountRaw());
    }

    private ScannedLog transferLog(int logIndex, BigInteger amount) {
        return new ScannedLog(
                TOKEN,
                logIndex,
                List.of(
                        DepositDetector.TRANSFER_TOPIC,
                        addressTopic(SENDER),
                        addressTopic(RECIPIENT)),
                "0x" + String.format("%064x", amount));
    }

    private String addressTopic(String address) {
        return "0x" + "0".repeat(24) + address.substring(2);
    }
}
