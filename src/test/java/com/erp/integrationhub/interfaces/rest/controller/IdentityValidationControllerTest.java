package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.result.PersonalIdentityResult;
import com.erp.integrationhub.application.dto.result.PlateResult;
import com.erp.integrationhub.application.dto.result.RucResult;
import com.erp.integrationhub.application.usecase.QueryPersonalIdentityUseCase;
import com.erp.integrationhub.application.usecase.QueryPlateUseCase;
import com.erp.integrationhub.application.usecase.QueryRucUseCase;
import com.erp.integrationhub.infrastructure.security.TenantRateLimiter;
import com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler;
import com.erp.integrationhub.support.AuthenticatedUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IdentityValidationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QueryRucUseCase queryRucUseCase;

    @Mock
    private QueryPlateUseCase queryPlateUseCase;

    @Mock
    private QueryPersonalIdentityUseCase queryPersonalIdentityUseCase;

    @BeforeEach
    void setUp() {
        IdentityValidationController controller = new IdentityValidationController(
                queryRucUseCase,
                queryPlateUseCase,
                queryPersonalIdentityUseCase,
                new TenantRateLimiter(60, 60));
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
        when(queryRucUseCase.queryRuc(any())).thenReturn(result);

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
        PlateResult result = new PlateResult(
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
        when(queryPlateUseCase.queryPlate(any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/integrations/validate/placa/ABC123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placa").value("ABC123"))
                .andExpect(jsonPath("$.marca").value("NISSAN"))
                .andExpect(jsonPath("$.modelo").value("FRONTIER"));
    }

    @Test
    @DisplayName("GET /api/v1/identity-validations/lookup?type=DNI&number=72345678 - Should return personal data from Factiliza")
    void lookupDni_ShouldReturnPersonalDetails() throws Exception {
        PersonalIdentityResult dniResult = new PersonalIdentityResult(
                "DNI",
                "72345678",
                "JUAN CARLOS",
                "PEREZ",
                "GOMEZ",
                "PEREZ GOMEZ, JUAN CARLOS",
                "3",
                true,
                false
        );
        when(queryPersonalIdentityUseCase.queryPersonalIdentity(any(), eq("DNI"), eq("72345678")))
                .thenReturn(dniResult);

        mockMvc.perform(get("/api/v1/identity-validations/lookup")
                        .param("type", "DNI")
                        .param("number", "72345678")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DNI"))
                .andExpect(jsonPath("$.documentNumber").value("72345678"))
                .andExpect(jsonPath("$.firstName").value("JUAN CARLOS"))
                .andExpect(jsonPath("$.firstSurname").value("PEREZ"))
                .andExpect(jsonPath("$.secondSurname").value("GOMEZ"))
                .andExpect(jsonPath("$.fullName").value("PEREZ GOMEZ, JUAN CARLOS"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.source").value("RENIEC"));
    }

    @Test
    @DisplayName("GET /api/v1/identity-validations/lookup?type=RUC&number=20601234567 - Should return company data from Chequea.pe")
    void lookupRuc_ShouldReturnCompanyDetails() throws Exception {
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
        when(queryRucUseCase.queryRuc(any())).thenReturn(result);

        mockMvc.perform(get("/api/v1/identity-validations/lookup")
                        .param("type", "RUC")
                        .param("number", "20601234567")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("RUC"))
                .andExpect(jsonPath("$.documentNumber").value("20601234567"))
                .andExpect(jsonPath("$.businessName").value("AUTOMOTRIZ CENTRAL SAC"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.source").value("SUNAT"));
    }
}
