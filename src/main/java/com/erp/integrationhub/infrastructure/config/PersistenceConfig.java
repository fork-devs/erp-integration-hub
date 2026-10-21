package com.erp.integrationhub.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuracion de la demarcacion transaccional.
 *
 * <p>El interceptor transaccional de Spring y el aspecto que aplica el
 * contexto de taller compiten por el mismo orden de advice. Sin fijarlo, el
 * orden es indeterminado y el SET LOCAL puede ejecutarse FUERA de la
 * transaccion, donde no tiene efecto: la consulta se ejecutaria sin el
 * filtro de aislamiento.
 *
 * <p>Con el interceptor en el orden 0 y el aspecto en el 1, el aspecto queda
 * DENTRO de la transaccion, que es donde el SET LOCAL es aplicable.
 */
@Configuration
@EnableTransactionManagement(order = 0)
public class PersistenceConfig {

    /**
     * Orden del aspecto de Row-Level Security. En Spring, un valor de orden
     * MENOR significa mayor precedencia y, por tanto, un advice EXTERIOR. El
     * aspecto debe ser INTERIOR al interceptor transaccional, de modo que su
     * orden sea mayor que el de este ultimo.
     */
    public static final int TENANT_RLS_ASPECT_ORDER = 1;
}
