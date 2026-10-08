package com.erp.integrationhub.domain.model;

import java.io.Serializable;
import java.util.UUID;

public class ProviderConfig implements Serializable {
    private final UUID id;
    private final ProviderType provider;
    private final String baseUrl;
    private final String credentialsRef;
    private final int timeoutMs;
    private final boolean enabled;

    public ProviderConfig(UUID id, ProviderType provider, String baseUrl, String credentialsRef, int timeoutMs, boolean enabled) {
        this.id = id != null ? id : UUID.randomUUID();
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.credentialsRef = credentialsRef;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getCredentialsRef() {
        return credentialsRef;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
