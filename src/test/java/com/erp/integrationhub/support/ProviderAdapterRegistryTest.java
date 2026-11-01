package com.erp.integrationhub.support;

import com.erp.integrationhub.infrastructure.adapter.external.ApiSunatElectronicInvoicingAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.ChequeaPeRucValidationAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.StripePaymentGatewayAdapter;
import com.erp.integrationhub.infrastructure.adapter.external.SunarpVehicleValidationAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedApiSunatAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedChequeaPeAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedStripeAdapter;
import com.erp.integrationhub.infrastructure.adapter.simulated.SimulatedSunarpAdapter;
import com.erp.integrationhub.infrastructure.config.ProviderAdapterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying the environment-based registration of provider adapters in ProviderAdapterRegistry.
 */
class ProviderAdapterRegistryTest {

    @Test
    @DisplayName("With simulated mode in a development profile, simulated adapters are registered")
    void withSimulatedModeInDevelopmentSimulatedAdaptersAreRegistered() {
        ProviderAdapterRegistry registry = buildRegistry(true, "dev");

        assertInstanceOf(SimulatedApiSunatAdapter.class, registry.taxAuthorityEmitterPort("u", "t"));
        assertInstanceOf(SimulatedStripeAdapter.class, registry.paymentGatewayPort("k"));
        assertInstanceOf(SimulatedChequeaPeAdapter.class, registry.identityValidatorPort("u", "t"));
        assertInstanceOf(SimulatedSunarpAdapter.class, registry.vehicleValidatorPort("u", "t"));
    }

    @Test
    @DisplayName("Without simulated mode, real external adapters are registered")
    void withoutSimulatedModeRealAdaptersAreRegistered() {
        ProviderAdapterRegistry registry = buildRegistry(false, "dev");

        assertInstanceOf(ApiSunatElectronicInvoicingAdapter.class, registry.taxAuthorityEmitterPort("u", "t"));
        assertInstanceOf(StripePaymentGatewayAdapter.class, registry.paymentGatewayPort("k"));
        assertInstanceOf(ChequeaPeRucValidationAdapter.class, registry.identityValidatorPort("u", "t"));
        assertInstanceOf(SunarpVehicleValidationAdapter.class, registry.vehicleValidatorPort("u", "t"));
    }

    @Test
    @DisplayName("With simulated mode outside a development profile, startup fails with IllegalStateException")
    void withSimulatedModeOutsideDevelopmentStartupFails() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> buildRegistry(true, "prod"));

        assertTrue(exception.getMessage().contains("SIMULADOS"),
                "Message must explain that simulated adapters are disallowed in production: "
                        + exception.getMessage());
    }

    private ProviderAdapterRegistry buildRegistry(boolean simulatedMode, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new ProviderAdapterRegistry(simulatedMode, environment);
    }
}
