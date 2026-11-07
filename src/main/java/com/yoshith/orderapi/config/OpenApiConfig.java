package com.yoshith.orderapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("High-Performance Order Processing API")
                        .version("1.0.0")
                        .description("Enterprise-grade Order Processing Engine featuring Redis Cache-Aside caching, " +
                                "JPA Optimistic Locking (@Version) for zero overselling, and strictly-once Idempotency controls.")
                        .contact(new Contact()
                                .name("Yoshith Nagidi")
                                .email("yoshith.nagidi@gmail.com")
                                .url("https://github.com/Yoshith-1808"))
                        .license(new License().name("Apache 2.0")));
    }
}
