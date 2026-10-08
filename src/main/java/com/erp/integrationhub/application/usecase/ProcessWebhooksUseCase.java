package com.erp.integrationhub.application.usecase;

import com.erp.integrationhub.domain.model.ProviderType;

public interface ProcessWebhooksUseCase {
    boolean processWebhook(ProviderType provider, String payload, String signatureHeader);
}
