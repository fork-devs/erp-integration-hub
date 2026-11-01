package com.erp.integrationhub.application.dto.command;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

/**
 * Command to query a vehicle license plate.
 */
public record QueryPlateCommand(
        @JsonProperty("tenantId") UUID tenantId,
        @JsonProperty("plate") @JsonAlias("placa") String plate
) implements Serializable {

    // --- Backward compatibility accessor ---

    public String placa() {
        return plate;
    }
}
