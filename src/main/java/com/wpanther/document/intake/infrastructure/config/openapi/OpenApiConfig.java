package com.wpanther.document.intake.infrastructure.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for API documentation.
 *
 * Configures SpringDoc OpenAPI to generate API documentation for the REST API.
 * Access the Swagger UI at: http://localhost:8081/swagger-ui/index.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Intake Service API")
                        .description("Spring Boot 3.x microservice for receiving and validating Thai e-Tax XML documents via REST API and Kafka")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Platform Team")
                                .email("platform@example.com"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.txt")))
                .externalDocs(new ExternalDocumentation()
                        .description("Thai e-Tax Invoice Microservices Overview")
                        .url("https://github.com/Wongsawat/invoice-microservices"));
    }
}
