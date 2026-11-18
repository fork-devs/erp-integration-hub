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
            case "PENDIENTE", "PENDIENTE_SUNAT", "EN PROCESO", "PENDING", "PENDING_SUNAT" -> PENDING;
            case "ANULADO", "ANULADA", "VOIDED" -> VOIDED;
            default -> UNKNOWN;
        };
    }

    /**
     * Returns the canonical English status string exposed by integration-hub endpoints.
     */
    public String toSunatStatus() {
        return switch (this) {
            case ACCEPTED -> "ACCEPTED";
            case OBSERVED -> "OBSERVED";
            case REJECTED -> "REJECTED";
            case PENDING -> "PENDING_SUNAT";
            case VOIDED -> "VOIDED";
            case UNKNOWN -> "UNKNOWN";
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

    /**
     * Checks if the current state permits retrying declaration to SUNAT.
     *
     * @return true if pending or unknown
     */
    public boolean isRetryable() {
        return this == PENDING || this == UNKNOWN;
    }
}
