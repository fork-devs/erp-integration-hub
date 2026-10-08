package com.erp.integrationhub.infrastructure.adapter.external;

import com.erp.integrationhub.domain.exception.IntegrationValidationException;
import com.erp.integrationhub.domain.exception.ProviderUnavailableException;
import com.erp.integrationhub.domain.model.Moneda;
import com.erp.integrationhub.domain.model.ResultadoPago;
import com.erp.integrationhub.domain.model.SolicitudCobro;
import com.erp.integrationhub.domain.port.PasarelaDePagoPort;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.UUID;

public class StripePaymentGatewayAdapter implements PasarelaDePagoPort {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGatewayAdapter.class);

    private final String apiKey;
    private final boolean sandboxMode;

    public StripePaymentGatewayAdapter(String apiKey, boolean sandboxMode) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.sandboxMode = sandboxMode;
        if (!this.apiKey.isEmpty() && !this.apiKey.contains("mock")) {
            Stripe.apiKey = this.apiKey;
        }
    }

    @Override
    public ResultadoPago procesarCobro(SolicitudCobro solicitud) {
        log.info("[STRIPE ACL] Procesando cobro de {} {} con idempotencyKey={}",
                solicitud.getMoneda(), solicitud.getMonto(), solicitud.getIdempotencyKey());

        // Si estamos en modo sandbox con clave simulada (sin credencial real), devolvemos respuesta simulada
        if (sandboxMode && (apiKey.contains("mock") || apiKey.contains("sandbox") || apiKey.isBlank())) {
            String chargeId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
            String authCode = "AUTH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return ResultadoPago.exitoso(chargeId, authCode);
        }

        // Integración Real con API de Stripe
        try {
            long amountInCents = solicitud.getMonto().multiply(BigDecimal.valueOf(100)).longValue();
            String currency = solicitud.getMoneda() == Moneda.USD ? "usd" : "pen";

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDescription(solicitud.getDescripcion())
                    .setPaymentMethod(solicitud.getTokenTarjeta())
                    .setConfirm(true)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    );

            if (solicitud.getClienteEmail() != null && !solicitud.getClienteEmail().isBlank()) {
                paramsBuilder.setReceiptEmail(solicitud.getClienteEmail());
            }

            paramsBuilder.putMetadata("tenant_id", solicitud.getTenantId().toString());
            paramsBuilder.putMetadata("payment_id", solicitud.getPaymentId().toString());

            RequestOptions options = RequestOptions.builder()
                    .setApiKey(apiKey)
                    .setIdempotencyKey(solicitud.getIdempotencyKey())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build(), options);

            log.info("[STRIPE ACL] PaymentIntent creado con estado: {} (ID: {})", paymentIntent.getStatus(), paymentIntent.getId());

            return switch (paymentIntent.getStatus()) {
                case "succeeded" -> ResultadoPago.exitoso(paymentIntent.getId(), paymentIntent.getId());
                case "requires_action", "requires_confirmation" -> ResultadoPago.requiereAccion(paymentIntent.getId(), paymentIntent.getClientSecret());
                case "processing" -> new ResultadoPago(true, "PENDING", paymentIntent.getId(), paymentIntent.getClientSecret(), null, null);
                default -> ResultadoPago.fallido("Estado no exitoso de Stripe: " + paymentIntent.getStatus());
            };

        } catch (CardException e) {
            log.warn("[STRIPE ACL] Tarjeta declinada: {} (Code: {})", e.getMessage(), e.getCode());
            return ResultadoPago.fallido("Tarjeta rechazada: " + e.getUserMessage());
        } catch (StripeException e) {
            log.error("[STRIPE ACL] Error de comunicación con Stripe API: {}", e.getMessage());
            throw new ProviderUnavailableException("Error de comunicación con Stripe: " + e.getMessage());
        } catch (Exception e) {
            log.error("[STRIPE ACL] Error interno procesando pago en Stripe", e);
            throw new ProviderUnavailableException("Fallo inesperado al conectar con Stripe: " + e.getMessage());
        }
    }

    @Override
    public ResultadoPago consultarEstadoPago(String chargeId) {
        if (sandboxMode && (apiKey.contains("mock") || apiKey.contains("sandbox") || apiKey.isBlank())) {
            return ResultadoPago.exitoso(chargeId, "AUTH_CONFIRMED");
        }

        try {
            RequestOptions options = RequestOptions.builder().setApiKey(apiKey).build();
            PaymentIntent paymentIntent = PaymentIntent.retrieve(chargeId, options);
            if ("succeeded".equals(paymentIntent.getStatus())) {
                return ResultadoPago.exitoso(paymentIntent.getId(), paymentIntent.getId());
            } else {
                return new ResultadoPago(false, paymentIntent.getStatus(), paymentIntent.getId(), paymentIntent.getClientSecret(), null, null);
            }
        } catch (StripeException e) {
            log.error("[STRIPE ACL] Error consultando PaymentIntent {}: {}", chargeId, e.getMessage());
            throw new ProviderUnavailableException("Error consultando estado en Stripe: " + e.getMessage());
        }
    }
}
