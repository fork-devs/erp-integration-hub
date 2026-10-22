package com.erp.integrationhub.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un error interno queda registrado, y no expone su contenido al cliente.
 *
 * <p>El manejador global registra la excepcion con su traza y con el esquema
 * estructurado, y devuelve una respuesta generica: el diagnostico va al
 * registro, que es del operador; al cliente le llega un error que no dice
 * nada del interior del servicio.
 */
class InternalErrorLoggingTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(
                com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("El error interno se registra con su traza y la respuesta es generica")
    void elErrorInternoSeRegistraYLaRespuestaEsGenerica() {
        com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler manejador =
                new com.erp.integrationhub.interfaces.rest.advice.GlobalRestExceptionHandler();

        org.springframework.http.ProblemDetail respuesta =
                manejador.handleGeneralException(new IllegalStateException("Fallo interno con detalle secreto"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), respuesta.getStatus());
        assertFalse(respuesta.getDetail().contains("detalle secreto"),
                "La respuesta al cliente no puede contener el detalle del error: " + respuesta.getDetail());

        boolean registrado = appender.list.stream()
                .anyMatch(evento -> evento.getFormattedMessage().contains("error interno")
                        || evento.getFormattedMessage().contains("Unhandled internal server error"));
        assertTrue(registrado, "El error interno debe quedar registrado");
    }
}
