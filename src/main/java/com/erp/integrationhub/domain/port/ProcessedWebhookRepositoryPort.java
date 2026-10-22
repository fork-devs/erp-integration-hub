package com.erp.integrationhub.domain.port;

import java.util.UUID;

/**
 * Registry of processed webhooks.
 *
 * <p>A webhook delivery can be repeated: the provider retries until it gets
 * an acknowledgement. Without this registry, the re-delivery applied the
 * effect a second time: a payment was marked as paid twice, and an issued
 * voucher was recorded twice.
 */
public interface ProcessedWebhookRepositoryPort {

    /**
     * Registers the event as processed.
     *
     * @return true if it had ALREADY been processed, that is, if this
     *         delivery is a re-delivery
     */
    boolean register(UUID eventId, com.erp.integrationhub.domain.model.ProviderType provider);
}
