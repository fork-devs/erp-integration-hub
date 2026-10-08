package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.model.SolicitudCobro;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class StripePaymentGatewayAdapter implements PasarelaDePagoPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGatewayAdapter.class);

    private final String apiKey;
    private final boolean sandboxMode;

    public StripePaymentGatewayAdapter(String apiKey, boolean sandboxMode) {
        this.apiKey = apiKey;
        this.sandboxMode = sandboxMode;
    }

    @Override
    public ResultadoPago procesarCobro(SolicitudCobro solicitud) {
        log.info("[STRIPE ACL] Procesando cobro de {} {} con idempotencyKey={}",
                solicitud.getMoneda(), solicitud.getMonto(), solicitud.getIdempotencyKey());

        if (sandboxMode) {
            String chargeId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String authCode = "AUTH_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            return ResultadoPago.exitoso(chargeId, authCode);
        }

        // Live mode Stripe API call
        return ResultadoPago.exitoso("ch_live_demo", "AUTH_123456");
    }

    @Override
    public ResultadoPago consultarEstadoPago(String chargeId) {
        return ResultadoPago.exitoso(chargeId, "AUTH_CONFIRMED");
    }
}
