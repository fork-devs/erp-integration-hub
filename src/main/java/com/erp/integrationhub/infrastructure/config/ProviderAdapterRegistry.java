package com.erp.integrationhub.infrastructure.config;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Registro de los adaptadores de proveedor, segun el perfil.
 *
 * <p>Los adaptadores REALES hablan con SUNAT, Stripe, Chequea.pe y SUNARP. Los
 * SIMULADOS fabrican respuestas plausibles, y sus URL apuntan a un dominio
 * que no existe.
 *
 * <p>La diferencia no es academica: una respuesta simulada dice "ACEPTADO"
 * con un PDF que no existe, y un cobro simulado devuelve un identificador de
 * cargo que no corresponde a ningun cobro real. Si eso llega a la bitacora de
 * auditoria, el sistema afirma haber cobrado por un cobro que no ocurrio, o
 * haber emitido un comprobante que SUNAT jamas vio.
 *
 * <p>Por eso el registro SIMULADO solo es admisible en un perfil de
 * desarrollo, y el arranque **falla de forma explicita** cuando se pide el
 * modo simulado fuera de el. Un fallo ruidoso al arrancar es preferible a un
 * despliegue que parece funcionar y no contacta con nadie.
 */
@Configuration
public class ProviderAdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderAdapterRegistry.class);

    /** Perfiles en los que se admite un adaptador simulado. */
    private static final List<String> PERFILES_DE_DESARROLLO = List.of("dev", "local", "test");

    private final boolean modoSimulado;
    private final List<String> perfilesActivos;

    public ProviderAdapterRegistry(
            @Value("${erp.integration.simulated-adapters:false}") boolean modoSimulado,
            Environment environment) {
        this.modoSimulado = modoSimulado;
        this.perfilesActivos = List.of(environment.getActiveProfiles());

        if (modoSimulado && !perfilDeDesarrollo()) {
            throw new IllegalStateException(
                    "Se han pedido adaptadores de proveedor SIMULADOS (erp.integration.simulated-adapters=true) "
                    + "y el perfil activo no es de desarrollo (" + perfilesActivos + "). Un adaptador simulado "
                    + "fabrica respuestas que no corresponden a ningun proveedor real: un cobro simulado "
                    + "devuelve un identificador de cargo inexistente, y un comprobante simulado afirma "
                    + "haber sido aceptado por SUNAT. Desactive el modo simulado, o ejecute en un perfil "
                    + "de desarrollo.");
        }

        if (modoSimulado) {
            log.warn("ATENCION: se estan usando adaptadores de proveedor SIMULADOS. Las respuestas no "
                    + "corresponden a ningun proveedor real.");
        }
    }

    private boolean perfilDeDesarrollo() {
        return perfilesActivos.stream().anyMatch(PERFILES_DE_DESARROLLO::contains);
    }

    @Bean
    public EmisorTributarioPort emisorTributarioPort(
            @Value("${erp.integration.apisunat.base-url:https://sandbox.apisunat.pe}") String baseUrl,
            @Value("${erp.integration.apisunat.token:}") String token) {
        if (modoSimulado) {
            return new SimulatedApiSunatAdapter();
        }
        return new ApiSunatElectronicInvoicingAdapter(baseUrl, token, false);
    }

    @Bean
    public PasarelaDePagoPort pasarelaDePagoPort(
            @Value("${erp.integration.stripe.api-key:}") String apiKey) {
        if (modoSimulado) {
            return new SimulatedStripeAdapter();
        }
        return new StripePaymentGatewayAdapter(apiKey, false);
    }

    @Bean
    public ValidadorIdentidadPort validadorIdentidadPort(
            @Value("${erp.integration.chequea.base-url:https://api.chequea.pe/v1}") String baseUrl,
            @Value("${erp.integration.chequea.token:}") String token) {
        if (modoSimulado) {
            return new SimulatedChequeaPeAdapter();
        }
        return new ChequeaPeRucValidationAdapter(baseUrl, token, false);
    }

    @Bean
    public ValidadorVehicularPort validadorVehicularPort(
            @Value("${erp.integration.apiperu.base-url:https://apiperu.dev/api}") String baseUrl,
            @Value("${erp.integration.apiperu.token:}") String token) {
        if (modoSimulado) {
            return new SimulatedSunarpAdapter();
        }
        return new SunarpVehicleValidationAdapter(baseUrl, token, false);
    }
}
