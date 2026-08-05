package io.ipbreaker.wallet.application.ledger;

import java.math.BigInteger;
import java.util.List;

public record DoubleEntryPosting(List<PostingEntry> entries) {
    public DoubleEntryPosting {
        entries = List.copyOf(entries);
        if (entries.size() < 2) {
            throw new IllegalArgumentException("A posting requires at least two entries");
        }
        BigInteger debits = total(entries, "DEBIT");
        BigInteger credits = total(entries, "CREDIT");
        if (!debits.equals(credits)) {
            throw new IllegalArgumentException("Ledger posting is not balanced");
        }
    }

    public static DoubleEntryPosting deposit(
            long platformAccountId, long userAccountId, BigInteger amountRaw) {
        return new DoubleEntryPosting(List.of(
                new PostingEntry(platformAccountId, "DEBIT", amountRaw),
                new PostingEntry(userAccountId, "CREDIT", amountRaw)));
    }

    private static BigInteger total(List<PostingEntry> entries, String direction) {
        return entries.stream()
                .filter(entry -> direction.equals(entry.direction()))
                .map(PostingEntry::amountRaw)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public record PostingEntry(long accountId, String direction, BigInteger amountRaw) {
        public PostingEntry {
            if (accountId <= 0 || amountRaw == null || amountRaw.signum() <= 0) {
                throw new IllegalArgumentException("Ledger entry must have an account and positive amount");
            }
            if (!"DEBIT".equals(direction) && !"CREDIT".equals(direction)) {
                throw new IllegalArgumentException("Ledger entry direction is invalid");
            }
        }
    }
}
