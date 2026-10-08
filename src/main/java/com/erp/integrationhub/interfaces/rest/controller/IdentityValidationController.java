package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.command.ConsultarPlacaCommand;
import com.erp.integrationhub.application.dto.command.ConsultarRucCommand;
import com.erp.integrationhub.application.dto.result.PlacaResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.ConsultarPlacaUseCase;
import com.erp.integrationhub.application.usecase.ConsultarRucUseCase;
import com.erp.integrationhub.interfaces.rest.response.PlacaResponse;
import com.erp.integrationhub.interfaces.rest.response.RucResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/validate")
@Tag(name = "Identity & Vehicle Validation", description = "Validates Peruvian RUC against SUNAT/RENIEC and vehicle license plates against SUNARP with high-performance caching")
public class IdentityValidationController {

    private final ConsultarRucUseCase consultarRucUseCase;
    private final ConsultarPlacaUseCase consultarPlacaUseCase;

    public IdentityValidationController(ConsultarRucUseCase consultarRucUseCase, ConsultarPlacaUseCase consultarPlacaUseCase) {
        this.consultarRucUseCase = consultarRucUseCase;
        this.consultarPlacaUseCase = consultarPlacaUseCase;
    }

    @GetMapping("/ruc/{ruc}")
    @Operation(summary = "Validate RUC via SUNAT", description = "Retrieves fiscal address, status (HABIDO), condition (ACTIVO) with 30-day TTL local cache")
    public ResponseEntity<RucResponse> validateRuc(
            @PathVariable("ruc") String ruc,
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId) {

        UUID effectiveTenantId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        ConsultarRucCommand command = new ConsultarRucCommand(effectiveTenantId, ruc);
        RucResult result = consultarRucUseCase.consultarRuc(command);
        return ResponseEntity.ok(RucResponse.fromResult(result));
    }

    @GetMapping("/placa/{placa}")
    @Operation(summary = "Validate vehicle plate via SUNARP", description = "Retrieves VIN, brand, model, color, fuel type with 7-day TTL local cache")
    public ResponseEntity<PlacaResponse> validatePlaca(
            @PathVariable("placa") String placa,
            @RequestHeader(value = "X-Tenant-ID", required = false) UUID tenantId) {

        UUID effectiveTenantId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        ConsultarPlacaCommand command = new ConsultarPlacaCommand(effectiveTenantId, placa);
        PlacaResult result = consultarPlacaUseCase.consultarPlaca(command);
        return ResponseEntity.ok(PlacaResponse.fromResult(result));
    }
}
