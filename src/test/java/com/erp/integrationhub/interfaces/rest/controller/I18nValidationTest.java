package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.application.usecase.ProcessPaymentUseCase;
import com.erp.integrationhub.infrastructure.config.I18nConfig;
import com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class I18nValidationTest {

    private MockMvc mockMvc;

    @Mock
    private ProcessPaymentUseCase processPaymentUseCase;

    private static final String INVALID_PAYLOAD = """
            {
                "amount": 10.0,
                "currency": "PEN",
                "cardToken": "",
                "description": "Test payment"
            }
            """;

    @BeforeEach
    void setUp() {
        I18nConfig config = new I18nConfig();
        MessageSource messageSource = config.messageSource();
        LocalValidatorFactoryBean validator = config.getValidator(messageSource);
        validator.afterPropertiesSet();

        PaymentIntegrationController controller = new PaymentIntegrationController(processPaymentUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setLocaleResolver(config.localeResolver())
                .setControllerAdvice(new GlobalRestExceptionHandler(messageSource))
                .build();
    }

    @Test
    @DisplayName("Accept-Language: es debe retornar mensaje de validación en español")
    void shouldReturnValidationErrorsInSpanish() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("Idempotency-Key", "key-test-123")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Violación de Restricción"))
                .andExpect(jsonPath("$.errors['cardToken']").value("El token de tarjeta o método de pago es obligatorio."));
    }

    @Test
    @DisplayName("Accept-Language: en debe retornar mensaje de validación en inglés")
    void shouldReturnValidationErrorsInEnglish() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("Idempotency-Key", "key-test-123")
                        .header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Constraint Violation"))
                .andExpect(jsonPath("$.errors['cardToken']").value("Card token or payment method ID is required."));
    }

    @Test
    @DisplayName("Sin header Accept-Language debe usar locale por defecto (es)")
    void shouldFallbackToDefaultLocaleWhenHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/integrations/payments/charge")
                        .header("Idempotency-Key", "key-test-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Violación de Restricción"))
                .andExpect(jsonPath("$.errors['cardToken']").value("El token de tarjeta o método de pago es obligatorio."));
    }
}
