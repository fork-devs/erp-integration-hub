package com.erp.integrationhub.support;

import com.erp.integrationhub.domain.model.EstadoComprobante;
import com.erp.integrationhub.domain.model.ResultadoDeclaracion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El estado del comprobante es explicito, y el codigo es el del proveedor.
 *
 * <p>El estado era TEXTO LIBRE, y de ahi venian dos defectos: un estado
 * desconocido se traducía a rechazo, y el codigo de respuesta se sustituia
 * por un literal, de modo que el diagnostico que llegaba al taller no era el
 * de la autoridad fiscal.
 */
class EstadoComprobanteTest {

    @Test
    @DisplayName("Un estado de observacion se traduce a OBSERVADO, y no a rechazo")
    void unEstadoDeObservacionNoSeConfundeConUnRechazo() {
        assertEquals(EstadoComprobante.OBSERVADO, EstadoComprobante.desdeProveedor("OBSERVADO"));
        assertEquals(EstadoComprobante.OBSERVADO, EstadoComprobante.desdeProveedor("observado"));
        assertEquals(EstadoComprobante.OBSERVADO, EstadoComprobante.desdeProveedor("CON OBSERVACIONES"));

        assertTrue(EstadoComprobante.OBSERVADO.emitido(),
                "Un comprobante observado ESTA emitido: no se puede volver a emitir");
        assertFalse(EstadoComprobante.OBSERVADO.reintentable(),
                "Un comprobante observado no se reintenta: ya existe");
    }

    @Test
    @DisplayName("Un estado desconocido no se convierte en un rechazo")
    void unEstadoDesconocidoNoSeConvierteEnRechazo() {
        assertEquals(EstadoComprobante.DESCONOCIDO, EstadoComprobante.desdeProveedor("ESTADO_NUEVO"));
        assertEquals(EstadoComprobante.DESCONOCIDO, EstadoComprobante.desdeProveedor(null));
        assertEquals(EstadoComprobante.DESCONOCIDO, EstadoComprobante.desdeProveedor("  "));
    }

    @Test
    @DisplayName("El codigo de respuesta es el que informo el proveedor")
    void elCodigoDeRespuestaEsElDelProveedor() {
        ResultadoDeclaracion resultado = ResultadoDeclaracion.conEstado(
                EstadoComprobante.RECHAZADO, "2335", "El comprobante fue observado", null, null, null, null);

        assertEquals("2335", resultado.codigoRespuestaSunat(),
                "El codigo del proveedor no puede sustituirse por un literal");
        assertEquals(EstadoComprobante.RECHAZADO, resultado.estado());
    }

    @Test
    @DisplayName("Un estado observado conserva su codigo y su mensaje")
    void unEstadoObservadoConservaSuCodigoYSuMensaje() {
        ResultadoDeclaracion resultado = ResultadoDeclaracion.observado(
                "0", "Aceptado con observacion", "pdf", "xml", "cdr", "hash");

        assertEquals(EstadoComprobante.OBSERVADO, resultado.estado());
        assertEquals("0", resultado.codigoRespuestaSunat());
        assertTrue(resultado.exitoso(), "Un comprobante observado ha sido aceptado");
    }
}
