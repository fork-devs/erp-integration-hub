package com.erp.integrationhub.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de peticiones por taller para las consultas de identidad.
 *
 * <p>Una consulta de RUC o de placa llama a un proveedor externo. Sin
 * limite, un taller (o un atacante que se haga pasar por uno) puede
 * saturar el proveedor externo con un volumen ilimitado de consultas, y
 * agotar la cuota de la plataforma con la que todo el mundo comparte.
 *
 * <p>El limite es POR TALLER, porque el coste de la consulta se paga entre
 * todos los que comparten la cuota del proveedor.
 */
@Component
public class TenantRateLimiter {

    private final int maximoPorVentana;
    private final Duration ventana;
    private final Map<UUID, Ventana> ventanas = new ConcurrentHashMap<>();

    public TenantRateLimiter(
            @org.springframework.beans.factory.annotation.Value("${erp.integration.identity-rate-limit.max:60}") int maximoPorVentana,
            @org.springframework.beans.factory.annotation.Value("${erp.integration.identity-rate-limit.window-seconds:60}") long ventanaSegundos) {
        this.maximoPorVentana = maximoPorVentana;
        this.ventana = Duration.ofSeconds(ventanaSegundos);
    }

    /**
     * Intenta consumir una unidad de la cuota del taller.
     *
     * @return true si la peticion se admite
     */
    public boolean intentar(UUID tenantId) {
        if (tenantId == null) {
            return true;
        }
        Instant ahora = Instant.now();
        ventanas.compute(tenantId, (taller, existente) -> {
            if (existente == null || existente.expiraEn.isBefore(ahora)) {
                return new Ventana(ahora.plus(this.ventana), new AtomicInteger(1));
            }
            existente.contador.incrementAndGet();
            return existente;
        });
        Ventana ventana = ventanas.get(tenantId);
        return ventana != null && ventana.contador.get() <= maximoPorVentana;
    }

    /**
     * Tiempo que hay que esperar para que la cuota se libere. Cero si la
     * peticion se admite.
     */
    public long segundosDeEspera(UUID tenantId) {
        if (tenantId == null) {
            return 0;
        }
        Ventana ventana = ventanas.get(tenantId);
        if (ventana == null) {
            return 0;
        }
        long restante = Duration.between(Instant.now(), ventana.expiraEn).getSeconds();
        return Math.max(restante, 1);
    }

    private static final class Ventana {
        private final Instant expiraEn;
        private final AtomicInteger contador;

        private Ventana(Instant expiraEn, AtomicInteger contador) {
            this.expiraEn = expiraEn;
            this.contador = contador;
        }
    }
}
