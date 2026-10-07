package com.ecommercie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de contexto (Fase 1).
 *
 * Sobe a aplicação inteira contra um Postgres REAL em container, com o Flyway
 * aplicando as migrations (V1). Se o contexto carrega, o pipeline
 * Spring + Postgres + Flyway está funcionando ponta a ponta.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EcommercieApplicationTests {

    @Test
    void contextLoads() {
        // vazio de propósito: o teste falha se o contexto (datasource/flyway/jpa) não subir
    }

    @Autowired
    ApplicationContext context;

    @Test
    void openInViewDesligado() {
        // sem OSIV, cada transacao tem seu proprio EntityManager: nada de ler entidade em cache
        // entre transacoes, e acesso lazy fora de transacao falha logo em vez de funcionar por acaso
        assertThat(context.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }
}
