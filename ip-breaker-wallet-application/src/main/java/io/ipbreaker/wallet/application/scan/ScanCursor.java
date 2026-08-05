package io.ipbreaker.wallet.application.scan;

public record ScanCursor(long lastScannedBlock, String lastScannedHash) {
}
