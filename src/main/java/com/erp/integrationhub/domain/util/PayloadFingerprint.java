package com.erp.integrationhub.domain.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Huella criptografica de una carga util.
 *
 * <p>La huella es lo que distingue "la misma peticion repetida" de "otra
 * peticion con la misma clave de idempotencia". Sin ella, la segunda
 * peticion recibia el resultado de la primera, y el cobro de la segunda no se
 * hacia.
 *
 * <p>Es SHA-256, y no un hash de 32 bits: con 32 bits, dos cargas utiles
 * distintas se confunden con una probabilidad de una entre cuatro mil, y esa
 * confusion es precisamente la que hay que evitar.
 */
public final class PayloadFingerprint {

    private PayloadFingerprint() {
    }

    /**
     * Huella de una carga util, en hexadecimal.
     */
    public static String of(String cargaUtil) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((cargaUtil != null ? cargaUtil : "").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
