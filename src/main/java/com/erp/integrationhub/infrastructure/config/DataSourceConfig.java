package com.erp.integrationhub.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Fuentes de datos del hub.
 *
 * <p>Hay dos, y son deliberadamente distintas:
 *
 * <ul>
 *   <li><strong>La de la aplicacion</strong> ({@code dataSource}), con el rol
 *       de ejecucion, que esta sujeto a la politica de Row-Level Security.
 *       Es la que usan la persistencia y la API.</li>
 *   <li><strong>La de plataforma</strong> ({@code platformDataSource}), con el
 *       rol que tiene BYPASSRLS. La usa EXCLUSIVAMENTE el relay de reintentos
 *       para recorrer la cola de pendientes de todos los talleres, porque es un
 *       proceso de la plataforma y todavia no conoce el taller de cada
 *       mensaje.</li>
 * </ul>
 *
 * <p>La principal se declara de forma explicita porque, al existir una segunda
 * fuente de datos, el auto-config de Spring retira la suya y la JPA acabaria
 * usando la de plataforma, es decir, la que elude el aislamiento.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Bean("platformDataSource")
    public DataSource platformDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${erp.security.platform-role.user}") String user,
            @Value("${erp.security.platform-role.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(user)
                .password(password)
                .build();
    }
}
