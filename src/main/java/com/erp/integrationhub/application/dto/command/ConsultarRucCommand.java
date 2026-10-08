package com.erp.integrationhub.application.dto.command;

import java.util.UUID;

public record ConsultarRucCommand(
        UUID tenantId,
        String ruc
) {}
