package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.port.EmisorTributarioPort;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.erp.integrationhub.domain.port.ValidadorIdentidadPort;
import com.erp.integrationhub.domain.port.ValidadorVehicularPort;
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
 * El registro de adaptadores de proveedor.
 *
 * <p>Un adaptador simulado fabrica respuestas que no corresponden a ningun
 * proveedor real: un cobro simulado devuelve un identificador de cargo
 * inexistente, y un comprobante simulado afirma haber sido aceptado por
 * SUNAT. Eso en la bitacora de auditoria es un comprobante y un cobro
 * fantasma.
 *
 * <p>Por eso el modo simulado solo es admisible en un perfil de desarrollo, y
 * el arranque falla de forma explicita en el resto.
 */
class ProviderAdapterRegistryTest {

    @Test
    @DisplayName("Con el modo simulado en un perfil de desarrollo, se registran los adaptadores simulados")
    void conElModoSimuladoEnDesarrolloSeRegistranLosSimulados() {
        ProviderAdapterRegistry registro = registro(true, "dev");

        assertInstanceOf(SimulatedApiSunatAdapter.class, registro.emisorTributarioPort("u", "t"));
        assertInstanceOf(SimulatedStripeAdapter.class, registro.pasarelaDePagoPort("k"));
        assertInstanceOf(SimulatedChequeaPeAdapter.class, registro.validadorIdentidadPort("u", "t"));
        assertInstanceOf(SimulatedSunarpAdapter.class, registro.validadorVehicularPort("u", "t"));
    }

    @Test
    @DisplayName("Sin el modo simulado, se registran los adaptadores reales")
    void sinElModoSimuladoSeRegistranLosReales() {
        ProviderAdapterRegistry registro = registro(false, "dev");

        assertInstanceOf(ApiSunatElectronicInvoicingAdapter.class, registro.emisorTributarioPort("u", "t"));
        assertInstanceOf(StripePaymentGatewayAdapter.class, registro.pasarelaDePagoPort("k"));
        assertInstanceOf(ChequeaPeRucValidationAdapter.class, registro.validadorIdentidadPort("u", "t"));
        assertInstanceOf(SunarpVehicleValidationAdapter.class, registro.validadorVehicularPort("u", "t"));
    }

    @Test
    @DisplayName("Con el modo simulado fuera de un perfil de desarrollo, el arranque falla")
    void conElModoSimuladoFueraDeDesarrolloElArranqueFalla() {
        IllegalStateException excepcion = assertThrows(IllegalStateException.class,
                () -> registro(true, "prod"));

        assertTrue(excepcion.getMessage().contains("SIMULADOS"),
                "El mensaje debe explicar que los adaptadores simulados no valen fuera de desarrollo: "
                        + excepcion.getMessage());
    }

    private ProviderAdapterRegistry registro(boolean modoSimulado, String... perfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(perfiles);
        return new ProviderAdapterRegistry(modoSimulado, environment);
    }
}
