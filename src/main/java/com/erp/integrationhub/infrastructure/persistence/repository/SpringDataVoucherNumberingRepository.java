package com.erp.integrationhub.infrastructure.persistence.repository;

import com.erp.integrationhub.infrastructure.persistence.entity.VoucherNumberingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SpringDataVoucherNumberingRepository
        extends JpaRepository<VoucherNumberingJpaEntity, VoucherNumberingJpaEntity.Key> {

    boolean existsByTenantIdAndSerieAndNumero(UUID tenantId, String serie, Integer numero);
}
