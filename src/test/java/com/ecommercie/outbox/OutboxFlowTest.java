package com.ecommercie.outbox;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.outbox.dispatcher.OutboxDispatcher;
import com.ecommercie.outbox.enums.OutboxStatus;
import com.ecommercie.outbox.models.OutboxEvent;
import com.ecommercie.outbox.relay.OutboxRelay;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.outbox.service.EmailService;
import com.ecommercie.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Testes de integração do outbox (Fase 7).
 *
 * Foco: o relay processa o que está PENDING e vencido → marca SENT e despacha;
 * se o envio falha, reagenda (não perde) e vira FAILED após N tentativas.
 * O EmailService é mockado (@MockitoBean) — nenhum e-mail real é enviado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OutboxFlowTest {

    @Autowired OutboxService outboxService;
    @Autowired OutboxRelay outboxRelay;
    @Autowired OutboxEventRepository outboxEventRepository;

    @MockitoBean EmailService emailService;   // substitui o bean real por um mock

    @BeforeEach
    void limpar() {
        outboxEventRepository.deleteAll();
    }

    private void registrarConfirmacaoPedido(String orderId, String email) {
        outboxService.registrar(OutboxTypes.EMAIL_CONFIRMACAO_PEDIDO,
                new OutboxDispatcher.EmailPayload(orderId, email));
    }

    @Test
    void relay_processaPendente_marcaSent_eDespachaEmail() {
        registrarConfirmacaoPedido("pedido-1", "cli@test.com");

        outboxRelay.processOutbox();

        // o e-mail foi despachado com o payload certo
        verify(emailService).enviarConfirmacaoPedido("pedido-1", "cli@test.com");

        // e o evento virou SENT
        OutboxEvent evento = outboxEventRepository.findAll().get(0);
        assertThat(evento.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(evento.getProcessedAt()).isNotNull();
    }

    @Test
    void relay_falhaNoEnvio_reagenda_naoPerde() {
        doThrow(new RuntimeException("SMTP fora do ar"))
                .when(emailService).enviarConfirmacaoPedido(anyString(), anyString());

        registrarConfirmacaoPedido("pedido-1", "cli@test.com");

        outboxRelay.processOutbox();

        OutboxEvent evento = outboxEventRepository.findAll().get(0);
        assertThat(evento.getStatus()).isEqualTo(OutboxStatus.PENDING);        // não perdeu
        assertThat(evento.getAttempts()).isEqualTo(1);                          // tentou 1x
        assertThat(evento.getNextAttemptAt()).isAfter(LocalDateTime.now());     // reagendado (backoff)
    }

    @Test
    void relay_apos5Tentativas_marcaFailed() {
        doThrow(new RuntimeException("SMTP fora do ar"))
                .when(emailService).enviarConfirmacaoPedido(anyString(), anyString());

        registrarConfirmacaoPedido("pedido-1", "cli@test.com");

        // simula que já falhou 4 vezes e está elegível de novo
        OutboxEvent e = outboxEventRepository.findAll().get(0);
        e.setAttempts(4);
        e.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));
        outboxEventRepository.save(e);

        outboxRelay.processOutbox();   // 5ª tentativa falha → desiste

        OutboxEvent depois = outboxEventRepository.findAll().get(0);
        assertThat(depois.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(depois.getAttempts()).isEqualTo(5);
    }

    @Test
    void relay_tipoDesconhecido_reagenda_semEnviarEmail() {
        outboxService.registrar("TIPO_INEXISTENTE",
                new OutboxDispatcher.EmailPayload("pedido-1", "cli@test.com"));

        outboxRelay.processOutbox();

        OutboxEvent evento = outboxEventRepository.findAll().get(0);
        assertThat(evento.getStatus()).isEqualTo(OutboxStatus.PENDING);   // default -> throw -> markFailed
        assertThat(evento.getAttempts()).isEqualTo(1);
        verifyNoInteractions(emailService);                               // nenhum e-mail
    }
}
