package com.erp.integrationhub.infrastructure.config;

import com.erp.integrationhub.domain.port.IdentityValidatorPort;
import com.erp.integrationhub.domain.port.PaymentGatewayPort;
import com.erp.integrationhub.domain.port.PersonalIdentityValidatorPort;
import com.erp.integrationhub.domain.port.TaxAuthorityEmitterPort;
import com.erp.integrationhub.domain.port.VehicleValidatorPort;
import com.erp.integrationhub.infrastructure.adapter.external.ApiSunatElectronicInvoicingAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.ChequeaPeRucValidationAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.FactilizaIdentityValidationAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.StripePaymentGatewayAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.SunarpVehicleValidationAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedApiSunatAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedChequeaPeAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedFactilizaAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedStripeAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedSunarpAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Registry configuring external provider adapters based on active Spring profile.
 *
 * <p>Ensures simulated mock adapters are strictly restricted to local, dev, and test profiles.
 */
@Configuration
public class ProviderAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderAdapterRegistry.class);

    private static final List<String> DEVELOPMENT_PROFILES = List.of("dev", "local", "test");

    private final boolean simulatedMode;
    private final List<String> activeProfiles;

    public ProviderAdapterRegistry(
            @Value("${erp.integration.simulated-adapters:false}") boolean simulatedMode,
            Environment environment) {
        this.simulatedMode = simulatedMode;
        this.activeProfiles = List.of(environment.getActiveProfiles());

        if (simulatedMode && !isDevelopmentProfile()) {
            throw new IllegalStateException(
                    "Simulated provider adapters (SIMULADOS) requested (erp.integration.simulated-adapters=true) "
                    + "in non-development profile (" + activeProfiles + "). Simulated adapters produce fake "
                    + "responses not verified by any actual provider. Disable simulated mode or switch to a dev profile.");
        }

        if (simulatedMode) {
            log.warn("WARNING: Simulated provider adapters are active. Responses will not communicate with actual upstream services.");
        }
    }

    private boolean isDevelopmentProfile() {
        return activeProfiles.stream().anyMatch(DEVELOPMENT_PROFILES::contains);
    }

    @Bean
    public TaxAuthorityEmitterPort taxAuthorityEmitterPort(
            @Value("${erp.integration.apisunat.base-url:https://sandbox.apisunat.pe}") String baseUrl,
            @Value("${erp.integration.apisunat.token:}") String token) {
        if (simulatedMode) {
            return new SimulatedApiSunatAdapter();
        }
        return new ApiSunatElectronicInvoicingAdapter(baseUrl, token, false);
    }

    @Bean
    public PaymentGatewayPort paymentGatewayPort(
            @Value("${erp.integration.stripe.api-key:}") String apiKey) {
        if (simulatedMode) {
            return new SimulatedStripeAdapter();
        }
        return new StripePaymentGatewayAdapter(apiKey, false);
    }

    @Bean
    public IdentityValidatorPort identityValidatorPort(
            @Value("${erp.integration.chequea.base-url:https://api.chequea.pe/v1}") String baseUrl,
            @Value("${erp.integration.chequea.token:}") String token,
            @Value("${erp.integration.chequea.sandbox-mode:false}") boolean sandboxMode) {
        if (simulatedMode || sandboxMode) {
            return new SimulatedChequeaPeAdapter();
        }
        return new ChequeaPeRucValidationAdapter(baseUrl, token, false);
    }

    @Bean
    public VehicleValidatorPort vehicleValidatorPort(
            @Value("${erp.integration.apiperu.base-url:https://apiperu.dev/api}") String baseUrl,
            @Value("${erp.integration.apiperu.token:}") String token) {
        if (simulatedMode) {
            return new SimulatedSunarpAdapter();
        }
        return new SunarpVehicleValidationAdapter(baseUrl, token, false);
    }

    @Bean
    public PersonalIdentityValidatorPort personalIdentityValidatorPort(
            @Value("${erp.integration.factiliza.base-url:https://api.factiliza.com/pe/v1}") String baseUrl,
            @Value("${erp.integration.factiliza.token:}") String token,
            @Value("${erp.integration.factiliza.sandbox-mode:false}") boolean sandboxMode) {
        if (simulatedMode || sandboxMode) {
            return new SimulatedFactilizaAdapter();
        }
        return new FactilizaIdentityValidationAdapter(baseUrl, token);
    }

    // --- Backward compatibility bean aliases ---

    public TaxAuthorityEmitterPort emisorTributarioPort(String baseUrl, String token) {
        return taxAuthorityEmitterPort(baseUrl, token);
    }

    public PaymentGatewayPort pasarelaDePagoPort(String apiKey) {
        return paymentGatewayPort(apiKey);
    }

    public IdentityValidatorPort validadorIdentidadPort(String baseUrl, String token) {
        return identityValidatorPort(baseUrl, token);
    }

    public VehicleValidatorPort validadorVehicularPort(String baseUrl, String token) {
        return vehicleValidatorPort(baseUrl, token);
    }
}
