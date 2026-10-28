package com.erp.integrationhub.application.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

/**
 * Command to query a taxpayer registration number (RUC).
 */
public record QueryRucCommand(
        @JsonProperty("tenantId") UUID tenantId,
        @JsonProperty("ruc") String ruc
) implements Serializable {
}
