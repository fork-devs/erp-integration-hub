package com.erp.integrationhub.domain.port;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Port for executing an operation within a scoped tenant context.
 */
public interface TenantContextPort {

    <T> T runInTenantContext(UUID tenantId, Supplier<T> action);

    default void runInTenantContext(UUID tenantId, Runnable action) {
        runInTenantContext(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
