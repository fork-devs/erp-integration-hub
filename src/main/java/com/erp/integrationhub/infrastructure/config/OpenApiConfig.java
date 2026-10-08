package com.erp.integrationhub.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI integrationHubOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ERP Integration Hub API")
                .description("Anti-Corruption Layer (ACL) for SUNAT (APISUNAT), Stripe Payments, and Chequea.pe/SUNARP Validations")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("Engineering Team")
                    .email("dev@erp-automotive.pe"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://erp-automotive.pe/terms")));
    }
}
