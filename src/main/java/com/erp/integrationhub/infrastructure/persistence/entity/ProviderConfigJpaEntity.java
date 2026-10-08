package com.erp.integrationhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_configs")
public class ProviderConfigJpaEntity {

    @Id
    private UUID id;

    @Column(name = "provider", nullable = false, unique = true, length = 50)
    private String provider;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "credentials_ref", nullable = false, length = 255)
    private String credentialsRef;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    public ProviderConfigJpaEntity() {}

    public ProviderConfigJpaEntity(UUID id, String provider, String baseUrl, String credentialsRef, Integer timeoutMs, Boolean enabled, Instant createdAt) {
        this.id = id;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.credentialsRef = credentialsRef;
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCredentialsRef() {
        return credentialsRef;
    }

    public void setCredentialsRef(String credentialsRef) {
        this.credentialsRef = credentialsRef;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
