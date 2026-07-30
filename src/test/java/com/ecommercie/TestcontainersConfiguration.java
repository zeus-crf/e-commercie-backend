package com.ecommercie;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provê um Postgres real (via Testcontainers) para os testes de integração.
 *
 * Uso: anote a classe de teste com {@code @Import(TestcontainersConfiguration.class)}.
 * O {@code @ServiceConnection} injeta url/usuário/senha do container no datasource
 * automaticamente — não precisa configurar spring.datasource.* no application-test.yml.
 *
 * O Spring Boot gerencia o ciclo de vida do container (start/stop) por ser um bean Startable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Ajuste a tag se precisar de outra versao do Postgres.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
