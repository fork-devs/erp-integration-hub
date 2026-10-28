package com.erp.integrationhub.domain.model;

import java.io.Serializable;

/**
 * Status of an electronic voucher or invoice before the tax authority (SUNAT).
 *
 * <p>{@code OBSERVED} is distinct from {@code REJECTED}: an observed invoice has been
 * ACCEPTED with observations by SUNAT and cannot be re-issued.
 */
public enum InvoiceStatus implements Serializable {

    /** Accepted by the tax authority. */
    ACCEPTED,

    /**
     * Accepted with observations by SUNAT. The invoice exists and cannot be re-issued.
     */
    OBSERVED,

    /** Definitively rejected by the tax authority: document was not issued. */
    REJECTED,

    /** Sent and awaiting processing/response from the tax authority. */
    PENDING,

    /** Voided / annulled. */
    VOIDED,

    /** Issued but in an unknown state from the provider. */
    UNKNOWN;

    /** Compatibility alias for PENDING state matching API contract. */
    public static final String PENDIENTE_SUNAT_ALIAS = "PENDIENTE_SUNAT";

    // Backward-compatible enum aliases
    public static final InvoiceStatus ACEPTADO = ACCEPTED;
    public static final InvoiceStatus OBSERVADO = OBSERVED;
    public static final InvoiceStatus RECHAZADO = REJECTED;
    public static final InvoiceStatus PENDIENTE = PENDING;
    public static final InvoiceStatus ANULADO = VOIDED;
    public static final InvoiceStatus DESCONOCIDO = UNKNOWN;

    /**
     * Translates a provider status string into a typed InvoiceStatus.
     *
     * @param status raw status string from provider
     * @return matching InvoiceStatus or UNKNOWN
     */
    public static InvoiceStatus fromProvider(String status) {
        if (status == null || status.isBlank()) {
            return UNKNOWN;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case "ACEPTADO", "ACEPTADA", "ACEPT", "ACCEPTED" -> ACCEPTED;
            case "OBSERVADO", "OBSERVADA", "CON OBSERVACIONES", "OBSERVED" -> OBSERVED;
            case "RECHAZADO", "RECHAZADA", "ERROR", "REJECTED" -> REJECTED;
            case "PENDIENTE", "PENDIENTE_SUNAT", "EN PROCESO", "PENDING" -> PENDING;
            case "ANULADO", "ANULADA", "VOIDED" -> VOIDED;
            default -> UNKNOWN;
        };
    }

    /** Backward compatibility alias for fromProvider. */
    public static InvoiceStatus desdeProveedor(String status) {
        return fromProvider(status);
    }

    /**
     * Returns the SUNAT protocol status string expected by consumers and regulatory endpoints.
     */
    public String toSunatStatus() {
        return switch (this) {
            case ACCEPTED -> "ACEPTADO";
            case OBSERVED -> "OBSERVADO";
            case REJECTED -> "RECHAZADO";
            case PENDING -> PENDIENTE_SUNAT_ALIAS;
            case VOIDED -> "ANULADO";
            case UNKNOWN -> "DESCONOCIDO";
        };
    }

    /**
     * Checks if the invoice has been issued (accepted or observed).
     *
     * @return true if accepted or observed
     */
    public boolean isIssued() {
        return this == ACCEPTED || this == OBSERVED;
    }

    /** Backward compatibility accessor for isIssued. */
    public boolean emitido() {
        return isIssued();
    }

    /**
     * Checks if the current state permits retrying declaration to SUNAT.
     *
     * @return true if pending or unknown
     */
    public boolean isRetryable() {
        return this == PENDING || this == UNKNOWN;
    }

    /** Backward compatibility accessor for isRetryable. */
    public boolean reintentable() {
        return isRetryable();
    }
}
