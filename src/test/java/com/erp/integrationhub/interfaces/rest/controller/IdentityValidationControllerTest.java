package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.result.PlacaResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.ConsultarPlacaUseCase;
import com.erp.integrationhub.application.usecase.ConsultarRucUseCase;
import com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler;
import com.erp.integrationhub.support.AuthenticatedUserContext;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IdentityValidationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ConsultarRucUseCase consultarRucUseCase;

    @Mock
    private ConsultarPlacaUseCase consultarPlacaUseCase;

    @BeforeEach
    void setUp() {
        IdentityValidationController controller = new IdentityValidationController(consultarRucUseCase, consultarPlacaUseCase);
        AuthenticatedUserContext.asTenant(UUID.randomUUID());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/integrations/validate/ruc/{ruc} - Should return 200 OK with RUC details")
    void validateRuc_ShouldReturn200() throws Exception {
        RucResult result = new RucResult(
                "20601234567",
                "AUTOMOTRIZ CENTRAL SAC",
                "ACTIVO",
                "HABIDO",
                "Av. Javier Prado 100",
                "LIMA",
                "LIMA",
                "SAN ISIDRO",
                "150131",
                true,
                false
        );
        when(consultarRucUseCase.consultarRuc(any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/integrations/validate/ruc/20601234567")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruc").value("20601234567"))
                .andExpect(jsonPath("$.razonSocial").value("AUTOMOTRIZ CENTRAL SAC"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.condicion").value("HABIDO"));
    }

    @Test
    @DisplayName("GET /api/v1/integrations/validate/placa/{placa} - Should return 200 OK with Plate details")
    void validatePlaca_ShouldReturn200() throws Exception {
        PlacaResult result = new PlacaResult(
                "ABC123",
                "NISSAN",
                "FRONTIER",
                "3N6DD09292831",
                "YD25-10293",
                "2022",
                "GRIS OSCURO",
                "DIESEL",
                true,
                false
        );
        when(consultarPlacaUseCase.consultarPlaca(any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/integrations/validate/placa/ABC123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placa").value("ABC123"))
                .andExpect(jsonPath("$.marca").value("NISSAN"))
                .andExpect(jsonPath("$.modelo").value("FRONTIER"));
    }
}
