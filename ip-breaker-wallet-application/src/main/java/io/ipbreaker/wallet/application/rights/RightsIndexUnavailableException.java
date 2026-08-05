package io.ipbreaker.wallet.application.rights;

public class RightsIndexUnavailableException extends RuntimeException {
    private final boolean rebuilding;

    public RightsIndexUnavailableException(boolean rebuilding) {
        super(rebuilding ? "Rights projection rebuild is in progress" : "Rights index is not ready");
        this.rebuilding = rebuilding;
    }

    public boolean rebuilding() {
        return rebuilding;
    }
}
