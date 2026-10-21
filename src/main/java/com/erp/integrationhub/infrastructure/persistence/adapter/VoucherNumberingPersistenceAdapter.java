package com.erp.integrationhub.infrastructure.persistence.adapter;

import com.erp.integrationhub.domain.exception.VoucherNumberingConflictException;
import com.erp.integrationhub.domain.port.VoucherNumberingPort;
import com.erp.integrationhub.infrastructure.persistence.entity.VoucherNumberingJpaEntity;
import com.erp.integrationhub.infrastructure.persistence.repository.SpringDataVoucherNumberingRepository;
import com.erp.integrationhub.infrastructure.persistence.TransactionGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class VoucherNumberingPersistenceAdapter implements VoucherNumberingPort {

    private final TransactionGuard transactionGuard;
    private final SpringDataVoucherNumberingRepository repository;

    public VoucherNumberingPersistenceAdapter(SpringDataVoucherNumberingRepository repository,
                                              TransactionGuard transactionGuard) {
        this.repository = repository;
        this.transactionGuard = transactionGuard;
    }

    @Override
    public void reserve(UUID tenantId, String serie, int numero, UUID comprobanteId) {
        transactionGuard.requireActiveTransaction("reserveVoucherNumbering");

        if (repository.existsByTenantIdAndSerieAndNumero(tenantId, serie, numero)) {
            throw new VoucherNumberingConflictException(serie, numero);
        }

        try {
            repository.saveAndFlush(new VoucherNumberingJpaEntity(
                    tenantId, serie, numero, comprobanteId, Instant.now()));
        } catch (RuntimeException e) {
            // Dos emisiones simultaneas pueden pasar la comprobacion y chocar
            // en la insercion: la clave primaria de la tabla es la que
            // resuelve la carrera, y el error se traduce al mismo conflicto.
            if (esViolacionDeUnicidad(e)) {
                throw new VoucherNumberingConflictException(serie, numero);
            }
            throw e;
        }
    }

    private boolean esViolacionDeUnicidad(Throwable error) {
        Throwable actual = error;
        while (actual != null) {
            if (actual instanceof org.hibernate.exception.ConstraintViolationException
                    || actual instanceof jakarta.persistence.EntityExistsException) {
                return true;
            }
            String mensaje = actual.getMessage();
            if (mensaje != null && mensaje.toLowerCase().contains("unique")) {
                return true;
            }
            actual = actual.getCause();
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReserved(UUID tenantId, String serie, int numero) {
        return repository.existsByTenantIdAndSerieAndNumero(tenantId, serie, numero);
    }
}
