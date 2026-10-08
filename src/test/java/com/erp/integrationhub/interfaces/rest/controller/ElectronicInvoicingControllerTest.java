package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.dto.result.ComprobanteResult;
import com.erp.integrationhub.application.usecase.EmitirComprobanteUseCase;
import com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ElectronicInvoicingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmitirComprobanteUseCase emitirComprobanteUseCase;

    @BeforeEach
    void setUp() {
        ElectronicInvoicingController controller = new ElectronicInvoicingController(emitirComprobanteUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/integrations/invoices/emit - Should return 200 OK with voucher response")
    void emitInvoice_WhenValidRequest_ShouldReturn200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID comprobanteId = UUID.randomUUID();

        ComprobanteResult mockResult = new ComprobanteResult(
                comprobanteId,
                true,
                "ACEPTADA",
                "0",
                "Factura aceptada por SUNAT",
                "https://storage.pe/f001-1.pdf",
                "https://storage.pe/f001-1.xml",
                "https://storage.pe/r-f001-1.zip",
                "HASH_CDR",
                false
        );
        when(emitirComprobanteUseCase.emitirComprobante(any())).thenReturn(mockResult);

        String jsonPayload = """
                {
                    "comprobanteId": "%s",
                    "tipo": "FACTURA",
                    "serie": "F001",
                    "numero": 100,
                    "clienteTipoDoc": "6",
                    "clienteNumDoc": "20601234567",
                    "clienteDenominacion": "EMPRESA TRANSPORTE SAC",
                    "totalGravada": 100.00,
                    "totalIgv": 18.00,
                    "total": 118.00,
                    "items": [
                        {
                            "unidadMedida": "NIU",
                            "descripcion": "Cambio de pastillas de freno",
                            "cantidad": 1,
                            "valorUnitario": 100.00,
                            "precioUnitario": 118.00,
                            "igv": 18.00,
                            "total": 118.00
                        }
                    ]
                }
                """.formatted(comprobanteId);

        mockMvc.perform(post("/api/v1/integrations/invoices/emit")
                        .header("X-Tenant-ID", tenantId.toString())
                        .header("Idempotency-Key", "idem-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitoso").value(true))
                .andExpect(jsonPath("$.estadoSunat").value("ACEPTADA"))
                .andExpect(jsonPath("$.pdfUrl").value("https://storage.pe/f001-1.pdf"));
    }
}
