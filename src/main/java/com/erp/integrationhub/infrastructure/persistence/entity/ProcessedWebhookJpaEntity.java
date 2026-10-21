package com.erp.integrationhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A webhook that has already been processed.
 *
 * <p>The event identifier is the primary key: it is what makes the
 * re-delivery detectable.
 */
@Entity
@Table(name = "processed_webhooks")
public class ProcessedWebhookJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public ProcessedWebhookJpaEntity() {
    }

    public ProcessedWebhookJpaEntity(UUID eventId, UUID tenantId, String provider, String eventType,
                                    Instant receivedAt) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.provider = provider;
        this.eventType = eventType;
        this.receivedAt = receivedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getProvider() {
        return provider;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
