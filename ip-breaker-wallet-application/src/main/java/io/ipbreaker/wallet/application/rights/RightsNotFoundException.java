package io.ipbreaker.wallet.application.rights;

public class RightsNotFoundException extends RuntimeException {
    private final Resource resource;

    public RightsNotFoundException(Resource resource) {
        super(resource + " not found");
        this.resource = resource;
    }

    public Resource resource() {
        return resource;
    }

    public enum Resource {
        IP_ASSET,
        LICENSE_AGREEMENT
    }
}
