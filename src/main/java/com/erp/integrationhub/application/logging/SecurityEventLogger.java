package com.erp.integrationhub.application.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registro estructurado de los eventos de seguridad.
 *
 * <p>El esquema es el MISMO que en el servicio de autenticacion:
 *
 * <pre>
 * evento=... tenant_id=... user_id=... trace_id=... resultado=... timestamp=...
 * </pre>
 *
 * <p>El esquema no admite contrasenas, tokens ni secretos: no hay ningun
 * parametro para ellos, de modo que no se pueden colar por descuido.
 */
public final class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    private SecurityEventLogger() {
    }

    public static void registrar(String evento,
                                  UUID tenantId,
                                  UUID userId,
                                  String traceId,
                                  String resultado,
                                  Map<String, String> extra) {
        log.info(formatear(evento, tenantId, userId, traceId, resultado, extra));
    }

    public static String formatear(String evento,
                                   UUID tenantId,
                                   UUID userId,
                                   String traceId,
                                   String resultado,
                                   Map<String, String> extra) {
        Map<String, String> atributos = new LinkedHashMap<>();
        atributos.put("evento", evento);
        atributos.put("tenant_id", tenantId != null ? tenantId.toString() : "-");
        atributos.put("user_id", userId != null ? userId.toString() : "-");
        atributos.put("trace_id", traceId != null && !traceId.isBlank() ? traceId : "-");
        atributos.put("resultado", resultado);
        atributos.put("timestamp", Instant.now().toString());

        if (extra != null) {
            extra.forEach((clave, valor) -> {
                if (clave != null && !clave.isBlank() && valor != null) {
                    atributos.put(clave, valor);
                }
            });
        }

        StringBuilder linea = new StringBuilder();
        atributos.forEach((clave, valor) -> {
            if (linea.length() > 0) {
                linea.append(' ');
            }
            linea.append(clave).append('=').append(valor);
        });
        return linea.toString();
    }
}
