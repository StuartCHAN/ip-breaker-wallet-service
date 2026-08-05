package io.ipbreaker.wallet.rights.event;

public class KnownEventDecodingException extends RuntimeException {
    public KnownEventDecodingException(String message) {
        super(message);
    }

    public KnownEventDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
