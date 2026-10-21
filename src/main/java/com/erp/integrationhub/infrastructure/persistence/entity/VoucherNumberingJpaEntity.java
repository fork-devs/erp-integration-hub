package com.erp.integrationhub.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Numeracion reservada de un comprobante.
 *
 * <p>La clave primaria es compuesta —taller, serie y numero—, que es
 * exactamente la identidad que no puede repetirse.
 */
@Entity
@Table(name = "voucher_numberings")
@IdClass(VoucherNumberingJpaEntity.Key.class)
public class VoucherNumberingJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "serie", nullable = false, length = 10)
    private String serie;

    @Id
    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Column(name = "comprobante_id", nullable = false)
    private UUID comprobanteId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public VoucherNumberingJpaEntity() {
    }

    public VoucherNumberingJpaEntity(UUID tenantId, String serie, Integer numero, UUID comprobanteId,
                                     Instant createdAt) {
        this.tenantId = tenantId;
        this.serie = serie;
        this.numero = numero;
        this.comprobanteId = comprobanteId;
        this.createdAt = createdAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSerie() {
        return serie;
    }

    public Integer getNumero() {
        return numero;
    }

    public UUID getComprobanteId() {
        return comprobanteId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Clave compuesta de la numeracion. */
    public static class Key implements Serializable {
        private UUID tenantId;
        private String serie;
        private Integer numero;

        public Key() {
        }

        public Key(UUID tenantId, String serie, Integer numero) {
            this.tenantId = tenantId;
            this.serie = serie;
            this.numero = numero;
        }

        public UUID getTenantId() {
            return tenantId;
        }

        public String getSerie() {
            return serie;
        }

        public Integer getNumero() {
            return numero;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(tenantId, key.tenantId)
                    && Objects.equals(serie, key.serie)
                    && Objects.equals(numero, key.numero);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, serie, numero);
        }
    }
}
