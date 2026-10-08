package com.erp.integrationhub.domain.port;

import com.erp.integrationhub.domain.model.ProviderConfig;
import com.erp.integrationhub.domain.model.ProviderType;

import java.util.List;
import java.util.Optional;

public interface ProviderConfigRepositoryPort {
    Optional<ProviderConfig> findByProvider(ProviderType provider);
    List<ProviderConfig> findAll();
    ProviderConfig save(ProviderConfig config);
}
