package com.erp.integrationhub.infrastructure.persistence.rls;

import com.erp.integrationhub.domain.port.TenantContextPort;
import com.erp.integrationhub.interfaces.rest.support.TenantContextScope;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Infrastructure adapter that binds the active {@link TenantContextHolder} for a scoped operation.
 */
@Component
public class TenantContextAdapter implements TenantContextPort {

    @Override
    public <T> T runInTenantContext(UUID tenantId, Supplier<T> action) {
        try (TenantContextScope ignored = TenantContextScope.forTenant(tenantId)) {
            return action.get();
        }
    }
}
