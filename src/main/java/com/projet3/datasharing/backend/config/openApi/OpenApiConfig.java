package com.projet3.datasharing.backend.config.openApi;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {
    /**
     * Swagger api/docs send JSON data in multi parts content as
     * Application/octect-stream, what is not supported as Content-Type.
     * This the reason of the following customizer. It helps to add the right
     * Application/JSON content-Type for the endpoints needing it.
     * 
     * @return
     */
    @Bean
    public OpenApiCustomizer multipartMetadataCustomizer() {

        return openApi -> {

            // Configure the metadata encoding for all multipart upload endpoints.
            List.of("/api/files", "/api/auth/files").forEach(path -> {

                var multipart = openApi.getPaths()
                        .get(path)
                        .getPost()
                        .getRequestBody()
                        .getContent()
                        .get("multipart/form-data");

                // Specify that metadata must be sent as JSON.
                var encoding = new Encoding();
                encoding.setContentType(MediaType.APPLICATION_JSON_VALUE);

                multipart.addEncoding("metadata", encoding);
            });
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {

        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
