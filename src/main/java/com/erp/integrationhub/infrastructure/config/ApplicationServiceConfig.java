package com.erp.integrationhub.infrastructure.config;

import com.erp.integrationhub.application.service.ElectronicInvoicingService;
import com.erp.integrationhub.application.service.IdentityValidationService;
import com.erp.integrationhub.application.service.PaymentIntegrationService;
import com.erp.integrationhub.application.service.RetrySchedulerService;
import com.erp.integrationhub.application.service.WebhookProcessingService;
import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.erp.integrationhub.infrastructure.adapter.cache.RedisIdentityCacheAdapter;
import com.erp.integrationhub.infrastructure.security.JwksKeyManager;
import org.springframework.beans.factory.annotation.Autowired;
import com.erp.integrationhub.infrastructure.adapter.external.ApiSunatElectronicInvoicingAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.ChequeaPeRucValidationAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.StripePaymentGatewayAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.SunarpVehicleValidationAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ApplicationServiceConfig {

    @Value("${erp.integration.apisunat.base-url:https://sandbox.apisunat.pe}")
    private String apisunatBaseUrl;

    @Value("${erp.integration.apisunat.token:sandbox_token_apisunat}")
    private String apisunatToken;

    @Value("${erp.integration.apisunat.sandbox-mode:true}")
    private boolean apisunatSandboxMode;

    @Value("${erp.integration.stripe.api-key:sk_test_mock_stripe_key}")
    private String stripeApiKey;

    @Value("${erp.integration.stripe.webhook-secret:whsec_test_mock_secret}")
    private String stripeWebhookSecret;

    @Value("${erp.integration.stripe.sandbox-mode:true}")
    private boolean stripeSandboxMode;

    @Value("${erp.integration.chequea.base-url:https://api.chequea.pe/v1}")
    private String chequeaBaseUrl;

    @Value("${erp.integration.chequea.token:sandbox_chequea_token}")
    private String chequeaToken;

    @Value("${erp.integration.chequea.sandbox-mode:true}")
    private boolean chequeaSandboxMode;

    @Value("${erp.integration.apiperu.base-url:https://apiperu.dev/api}")
    private String apiperuBaseUrl;

    @Value("${erp.integration.apiperu.token:sandbox_apiperu_token}")
    private String apiperuToken;

    @Value("${erp.integration.apiperu.sandbox-mode:true}")
    private boolean apiperuSandboxMode;

    @Bean
    public JwksKeyManager jwksKeyManager(
            @Value("${erp.security.jwks-url:https://auth.erp-automotriz.com/.well-known/jwks.json}") String jwksUrl,
            org.springframework.core.env.Environment environment) {
        return new JwksKeyManager(
                jwksUrl,
                java.util.Arrays.asList(environment.getActiveProfiles()));
    }

    @Bean
    public ElectronicInvoicingService electronicInvoicingService(
            EmisorTributarioPort emisorTributarioPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            UnitOfWork unitOfWork) {
        return new ElectronicInvoicingService(emisorTributarioPort, idempotencyRepositoryPort, attemptRepositoryPort, retryQueueRepositoryPort, unitOfWork);
    }

    @Bean
    public PaymentIntegrationService paymentIntegrationService(
            PasarelaDePagoPort pasarelaDePagoPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        return new PaymentIntegrationService(pasarelaDePagoPort, idempotencyRepositoryPort, attemptRepositoryPort, unitOfWork);
    }

    @Bean
    public IdentityCachePort identityCachePort(@Autowired(required = false) org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        return new RedisIdentityCacheAdapter(redisTemplate);
    }

    @Bean
    public IdentityValidationService identityValidationService(
            ValidadorIdentidadPort validadorIdentidadPort,
            ValidadorVehicularPort validadorVehicularPort,
            IdentityCachePort identityCachePort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        return new IdentityValidationService(validadorIdentidadPort, validadorVehicularPort, identityCachePort, attemptRepositoryPort, unitOfWork);
    }

    @Bean
    public WebhookProcessingService webhookProcessingService(
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            UnitOfWork unitOfWork) {
        return new WebhookProcessingService(stripeWebhookSecret, apisunatToken, attemptRepositoryPort, idempotencyRepositoryPort, unitOfWork);
    }

    @Bean
    public RetrySchedulerService retrySchedulerService(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            EmisorTributarioPort emisorTributarioPort,
            PasarelaDePagoPort pasarelaDePagoPort,
            UnitOfWork unitOfWork) {
        return new RetrySchedulerService(retryQueueRepositoryPort, attemptRepositoryPort, idempotencyRepositoryPort, emisorTributarioPort, pasarelaDePagoPort, unitOfWork);
    }
}
