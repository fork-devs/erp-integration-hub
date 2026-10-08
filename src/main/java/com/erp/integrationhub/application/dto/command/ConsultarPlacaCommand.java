package com.erp.integrationhub.application.dto.command;

import java.util.UUID;

public record ConsultarPlacaCommand(
        UUID tenantId,
        String placa
) {}
