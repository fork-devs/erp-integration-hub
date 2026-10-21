package com.erp.integrationhub.infrastructure.adapter.simulated;

import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.model.SolicitudCobro;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Pasarela de pago SIMULADA.
 *
 * <p>Un cobro simulado responde con un identificador de cargo que no
 * corresponde a ningun cobro real. Si esa respuesta llega a la bitacora de
 * auditoria, el sistema afirma haber cobrado por un cobro que no ocurrio.
 *
 * <p>Solo puede registrarse en un perfil de desarrollo: ver
 * {@code ProviderAdapterRegistry}.
 */
public class SimulatedStripeAdapter implements PasarelaDePagoPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedStripeAdapter.class);

    @Override
    public ResultadoPago procesarCobro(SolicitudCobro solicitud) {
        log.warn("[SIMULADO] Se procesa un cobro SIMULADO de {} {}: no se contacta con Stripe",
                solicitud.getMoneda(), solicitud.getMonto());

        String chargeId = "pi_simulado_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String authCode = "AUTH_SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ResultadoPago.exitoso(chargeId, authCode);
    }

    @Override
    public ResultadoPago consultarEstadoPago(String chargeId) {
        log.warn("[SIMULADO] Se consulta el estado de un cobro SIMULADO {}", chargeId);
        return ResultadoPago.exitoso(chargeId, "AUTH_SIM_CONFIRMED");
    }
}
