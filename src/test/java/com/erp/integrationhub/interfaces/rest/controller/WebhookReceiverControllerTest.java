package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessWebhooksUseCase;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebhookReceiverControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProcessWebhooksUseCase processWebhooksUseCase;

    @BeforeEach
    void setUp() {
        WebhookReceiverController controller = new WebhookReceiverController(processWebhooksUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/integrations/webhooks/stripe - Should receive and return 200 OK")
    void receiveStripeWebhook_ShouldReturn200() throws Exception {
        when(processWebhooksUseCase.processWebhook(any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/integrations/webhooks/stripe")
                        .header("Stripe-Signature", "sig_header_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment_intent.succeeded\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.processed").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/integrations/webhooks/nubefact - Should receive and return 200 OK")
    void receiveNubefactWebhook_ShouldReturn200() throws Exception {
        when(processWebhooksUseCase.processWebhook(any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/integrations/webhooks/nubefact")
                        .header("X-Nubefact-Signature", "nube_sig_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"cdr.generated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.processed").value(true));
    }
}
