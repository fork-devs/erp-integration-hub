package com.erp.integrationhub.infrastructure.config;

import com.erp.integrationhub.application.service.ElectronicInvoicingService;
import com.erp.integrationhub.application.service.IdentityValidationService;
import com.erp.integrationhub.application.service.PaymentIntegrationService;
import com.erp.integrationhub.application.service.RetrySchedulerService;
import com.erp.integrationhub.application.service.RetryTaskReactivationService;
import com.erp.integrationhub.application.service.WebhookProcessingService;
import com.erp.integrationhub.domain.port.IdempotencyRepositoryPort;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.erp.integrationhub.domain.port.IdentityValidatorPort;
import com.erp.integrationhub.domain.port.IntegrationAttemptRepositoryPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.ProcessedWebhookRepositoryPort;
import com.erp.integrationhub.domain.port.RetryQueueRepositoryPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.UnitOfWork;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
import com.erp.integrationhub.domain.port.VoucherNumberingPort;
import com.erp.integrationhub.domain.usecase.ReactivateRetryTaskUseCase;
import com.erp.integrationhub.infrastructure.adapter.cache.RedisIdentityCacheAdapter;
import com.erp.integrationhub.infrastructure.security.JwksKeyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

/**
 * Spring configuration registering core application services and adapters.
 */
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
    public ReactivateRetryTaskUseCase reactivateRetryTaskUseCase(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            UnitOfWork unitOfWork) {
        return new RetryTaskReactivationService(retryQueueRepositoryPort, unitOfWork);
    }

    @Bean
    public JwksKeyManager jwksKeyManager(
            @Value("${erp.security.jwks-url:https://auth.erp-automotriz.com/.well-known/jwks.json}") String jwksUrl,
            Environment environment) {
        return new JwksKeyManager(
                jwksUrl,
                Arrays.asList(environment.getActiveProfiles()));
    }

    @Bean
    public ElectronicInvoicingService electronicInvoicingService(
            TaxAuthorityEmitterPort taxAuthorityEmitterPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            UnitOfWork unitOfWork,
            VoucherNumberingPort voucherNumberingPort) {
        return new ElectronicInvoicingService(taxAuthorityEmitterPort, idempotencyRepositoryPort, attemptRepositoryPort,
                retryQueueRepositoryPort, unitOfWork, voucherNumberingPort);
    }

    @Bean
    public PaymentIntegrationService paymentIntegrationService(
            PaymentGatewayPort paymentGatewayPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        return new PaymentIntegrationService(paymentGatewayPort, idempotencyRepositoryPort, attemptRepositoryPort, unitOfWork);
    }

    @Bean
    public IdentityCachePort identityCachePort(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        return new RedisIdentityCacheAdapter(redisTemplate);
    }

    @Bean
    public IdentityValidationService identityValidationService(
            IdentityValidatorPort identityValidatorPort,
            VehicleValidatorPort vehicleValidatorPort,
            IdentityCachePort identityCachePort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            UnitOfWork unitOfWork) {
        return new IdentityValidationService(identityValidatorPort, vehicleValidatorPort, identityCachePort, attemptRepositoryPort, unitOfWork);
    }

    @Bean
    public WebhookProcessingService webhookProcessingService(
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            ProcessedWebhookRepositoryPort processedWebhookRepositoryPort,
            @Value("${erp.integration.webhook.signing-secret:}") String webhookSigningSecret,
            @Value("${erp.integration.webhook.window-seconds:300}") long timeWindowSeconds,
            UnitOfWork unitOfWork) {
        return new WebhookProcessingService(stripeWebhookSecret, apisunatToken, attemptRepositoryPort,
                idempotencyRepositoryPort, processedWebhookRepositoryPort, webhookSigningSecret,
                timeWindowSeconds, unitOfWork);
    }

    @Bean
    public RetrySchedulerService retrySchedulerService(
            RetryQueueRepositoryPort retryQueueRepositoryPort,
            IntegrationAttemptRepositoryPort attemptRepositoryPort,
            IdempotencyRepositoryPort idempotencyRepositoryPort,
            TaxAuthorityEmitterPort taxAuthorityEmitterPort,
            PaymentGatewayPort paymentGatewayPort,
            UnitOfWork unitOfWork) {
        return new RetrySchedulerService(retryQueueRepositoryPort, attemptRepositoryPort, idempotencyRepositoryPort, taxAuthorityEmitterPort, paymentGatewayPort, unitOfWork);
    }
}
