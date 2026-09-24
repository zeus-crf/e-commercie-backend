package com.ecommercie.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadados do OpenAPI e o esquema de autenticacao por cookie httpOnly.
 * Controllers autenticados referenciam {@link #COOKIE_AUTH} via @SecurityRequirement.
 */
@Configuration
public class OpenApiConfig {

    public static final String COOKIE_AUTH = "cookieAuth";

    @Value("${server.port:8080}")
    private String port;

    @Bean
    public OpenAPI ecommercieOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ecommercie API")
                        .version("v1")
                        .description("""
                                API REST de e-commerce.

                                **Envelope padrao:** as respostas seguem
                                `{ "success": boolean, "message": string|null, "data": T|null }`.
                                Campos nulos sao omitidos. Excecao: o webhook do Melhor Envio
                                (`/api/v1/webhooks/melhorenvio`) responde `200 OK` puro, sem envelope.

                                **Autenticacao:** cookies httpOnly `access_token` + `refresh_token`,
                                emitidos por `POST /api/v1/auth/login`. Nao ha token no corpo nem no header.

                                **Como testar pelo Swagger UI:** o botao "Authorize" nao consegue gravar
                                um cookie httpOnly. Chame `POST /api/v1/auth/login` aqui mesmo em "Try it out":
                                o navegador guarda os cookies e os envia nas chamadas seguintes.
                                Endpoints com cadeado exigem login; os de `/api/v1/admin/**` exigem ROLE_ADMIN.

                                **Paginacao:** `?page=&size=&sort=`.
                                """))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Local / Docker")))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")
                                .description("Cookie httpOnly emitido por POST /api/v1/auth/login")));
    }
}
