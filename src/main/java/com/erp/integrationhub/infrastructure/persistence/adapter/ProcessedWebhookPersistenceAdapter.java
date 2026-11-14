package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.exception.TenantContextMissingException;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.ProcessedWebhookRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.ProcessedWebhookJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataProcessedWebhookRepository;
import com.erp.integrationhub.infrastructure.persistence.rls.TenantContextHolder;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ProcessedWebhookPersistenceAdapter implements ProcessedWebhookRepositoryPort {

    private final TransactionGuard transactionGuard;
    private final SpringDataProcessedWebhookRepository repository;

    public ProcessedWebhookPersistenceAdapter(SpringDataProcessedWebhookRepository repository,
                                             TransactionGuard transactionGuard) {
        this.repository = repository;
        this.transactionGuard = transactionGuard;
    }

    @Override
    public boolean register(UUID eventId, ProviderType provider) {
        transactionGuard.requireActiveTransaction("registerProcessedWebhook");

        if (repository.existsById(eventId)) {
            return true;
        }

        try {
            // El identificador de evento es la clave primaria: dos entregas
            // simultaneas del mismo aviso no pueden registrarse las dos, y la
            // segunda sabe que es una reentrega.
            UUID tenantId = TenantContextHolder.getTenantId();
            if (tenantId == null) {
                throw new TenantContextMissingException("Processed webhook registration requires an active tenant context");
            }
            repository.saveAndFlush(new ProcessedWebhookJpaEntity(
                    eventId, tenantId, provider.name(), null, Instant.now()));
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }
}
