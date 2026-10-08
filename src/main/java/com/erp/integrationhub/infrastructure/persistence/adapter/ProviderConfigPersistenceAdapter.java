package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.model.ProviderConfig;
import com.erp.integrationhub.domain.model.ProviderType;
import com.erp.integrationhub.domain.port.ProviderConfigRepositoryPort;
import com.erp.integrationhub.infrastructure.persistence.entity.ProviderConfigJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataProviderConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ProviderConfigPersistenceAdapter implements ProviderConfigRepositoryPort {

    private final SpringDataProviderConfigRepository repository;

    public ProviderConfigPersistenceAdapter(SpringDataProviderConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ProviderConfig> findByProvider(ProviderType provider) {
        return repository.findByProvider(provider.name()).map(this::toDomain);
    }

    @Override
    public List<ProviderConfig> findAll() {
        return repository.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public ProviderConfig save(ProviderConfig config) {
        ProviderConfigJpaEntity entity = toEntity(config);
        ProviderConfigJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    private ProviderConfig toDomain(ProviderConfigJpaEntity entity) {
        return new ProviderConfig(
                entity.getId(),
                ProviderType.valueOf(entity.getProvider()),
                entity.getBaseUrl(),
                entity.getCredentialsRef(),
                entity.getTimeoutMs() != null ? entity.getTimeoutMs() : 5000,
                entity.getEnabled() != null ? entity.getEnabled() : true
        );
    }

    private ProviderConfigJpaEntity toEntity(ProviderConfig domain) {
        return new ProviderConfigJpaEntity(
                domain.getId(),
                domain.getProvider().name(),
                domain.getBaseUrl(),
                domain.getCredentialsRef(),
                domain.getTimeoutMs(),
                domain.isEnabled(),
                java.time.Instant.now()
        );
    }
}
