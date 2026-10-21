package com.erp.integrationhub.infrastructure.persistence.rls;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Ejecucion de consultas de alcance de plataforma.
 *
 * <p>El relay de reintentos recorre la cola de pendientes de TODOS los
 * talleres: es un proceso interno de la plataforma y no puede filtrar por
 * taller, porque todavia no lo conoce. Esa consulta necesita el rol de
 * plataforma, que es el unico con permiso de eludir la politica de
 * Row-Level Security.
 *
 * <p>La credencial vive SEPARADA de la de la aplicacion, de modo que un
 * fallo de seguridad en la aplicacion no puede escalarla hasta el alcance
 * transversal.
 */
@Component
public class PlatformScopeQueryExecutor {

    private final JdbcTemplate jdbcTemplate;

    public PlatformScopeQueryExecutor(@Qualifier("platformDataSource") DataSource platformDataSource) {
        this.jdbcTemplate = new JdbcTemplate(platformDataSource);
    }

    public List<Map<String, Object>> query(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }
}
