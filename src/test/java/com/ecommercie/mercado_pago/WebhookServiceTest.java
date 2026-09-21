package com.ecommercie.mercado_pago;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.fiscal.repository.InvoiceRepository;
import com.ecommercie.mercado_pago.dtos.MercadoPagoNotification;
import com.ecommercie.mercado_pago.models.WebhookEvent;
import com.ecommercie.mercado_pago.service.WebhookService;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.models.OutboxEvent;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.pedido.models.Address;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * Testes do webhook de pagamento (Fase 8 + hook fiscal da Fase 11).
 *
 * O WebhookService instancia o PaymentClient do SDK do Mercado Pago dentro do metodo, entao
 * nao ha como injetar um dublê: usamos mockConstruction, que intercepta o `new PaymentClient()`
 * feito la dentro. Nenhuma chamada sai para a internet.
 *
 * A classe NAO pode ser @Transactional: as fixtures precisam estar commitadas para o servico
 * enxerga-las, igual ao FiscalServiceTest.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WebhookServiceTest {

    @Autowired WebhookService webhookService;
    @Autowired WebhookEventRepository webhookEventRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired CartRepository cartRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;

    private final AtomicInteger seq = new AtomicInteger();

    private static final String PAYMENT_ID = "1234567890";

    // Limpa na ordem das FKs: invoice e pedido_item dependem de pedido; pedido depende de usuario.
    @BeforeEach
    void limpar() {
        webhookEventRepository.deleteAll();
        invoiceRepository.deleteAll();
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----------------- testes -----------------

    @Test
    void pagamentoAprovado_marcaPagoBaixaEstoqueEAgendaOsDoisEventos() throws Exception {
        Order pedido = pedidoAguardandoPagamento();

        processarPagamento(pedido.getId(), "approved");

        Order depois = orderRepository.findById(pedido.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrder.PAGO);
        assertThat(depois.getMpPaymentId()).isEqualTo(Long.parseLong(PAYMENT_ID));

        // reserva virou baixa: disponivel 10 -> 8, reservada 2 -> 0.
        // Lido pelo repositorio, e nao por depois.getItens(): a colecao e LAZY e o teste roda
        // fora de transacao, entao navegar pela entidade daria LazyInitializationException.
        assertThat(inventoryItemRepository.findAll()).singleElement().satisfies(estoque -> {
            assertThat(estoque.getDisponivel()).isEqualTo(8);
            assertThat(estoque.getReservada()).isZero();
        });

        // os DOIS eventos de outbox — o fiscal com o payload certo (era o bug do e-mail no payload)
        List<OutboxEvent> eventos = outboxEventRepository.findAll();
        assertThat(eventos).hasSize(2);
        assertThat(eventos).anySatisfy(ev -> {
            assertThat(ev.getType()).isEqualTo(OutboxTypes.EMAIL_CONFIRMACAO_PEDIDO);
            assertThat(ev.getPayload()).contains(pedido.getId()).contains("@");
        });
        assertThat(eventos).anySatisfy(ev -> {
            assertThat(ev.getType()).isEqualTo(OutboxTypes.FISCAL_EMISSAO);
            assertThat(ev.getPayload()).contains("orderId").contains(pedido.getId());
        });

        // e o evento de webhook registrado, que e o que garante a idempotencia
        assertThat(webhookEventRepository.existsByProviderAndExternalId("mercadopago", PAYMENT_ID)).isTrue();
    }

    @Test
    void pagamentoNaoAprovado_naoAlteraNada() throws Exception {
        Order pedido = pedidoAguardandoPagamento();

        processarPagamento(pedido.getId(), "pending");

        Order depois = orderRepository.findById(pedido.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrder.AGUARDANDO_PAGAMENTO);
        assertThat(depois.getMpPaymentId()).isNull();
        assertThat(outboxEventRepository.findAll()).isEmpty();
        assertThat(webhookEventRepository.existsByProviderAndExternalId("mercadopago", PAYMENT_ID)).isFalse();
    }

    @Test
    void notificacaoRepetida_eIgnorada() throws Exception {
        Order pedido = pedidoAguardandoPagamento();

        // o mesmo paymentId ja foi processado antes
        webhookEventRepository.save(WebhookEvent.builder()
                .provider("mercadopago").externalId(PAYMENT_ID).status("PROCESSED").build());

        processarPagamento(pedido.getId(), "approved");

        // nada aconteceu de novo: o pedido segue aguardando e nenhum evento foi gerado
        Order depois = orderRepository.findById(pedido.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrder.AGUARDANDO_PAGAMENTO);
        assertThat(outboxEventRepository.findAll()).isEmpty();
        assertThat(webhookEventRepository.findAll()).hasSize(1);
    }

    // ----------------- helpers -----------------

    /**
     * Executa o webhook com o PaymentClient do SDK interceptado — o `new PaymentClient()` de dentro
     * do servico devolve o mock, que responde com o status pedido e a referencia do pedido.
     */
    private void processarPagamento(String orderId, String status) throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(status);
        when(payment.getExternalReference()).thenReturn(orderId);

        try (MockedConstruction<PaymentClient> ignored = mockConstruction(PaymentClient.class,
                (mockClient, contexto) -> when(mockClient.get(anyLong())).thenReturn(payment))) {

            webhookService.processarNotificacao(new MercadoPagoNotification(
                    "payment.updated", "payment", null, null,
                    new MercadoPagoNotification.Data(PAYMENT_ID)));
        }
    }

    /** Pedido recem-saido do checkout: AGUARDANDO_PAGAMENTO, com 2 unidades reservadas no estoque. */
    private Order pedidoAguardandoPagamento() {
        int n = seq.incrementAndGet();

        User user = userRepository.save(User.builder()
                .nome("Cliente Webhook " + n)
                .email("webhook" + n + "@teste.com")
                .senha("$2a$10$naoimporta")
                .cpfCnpj("12345678909")
                .papel(Papel.CLIENTE)
                .ativo(true)
                .build());

        Category categoria = categoryRepository.save(Category.builder()
                .nome("Categoria " + n).slug("categoria-" + n).build());

        Product produto = productRepository.save(Product.builder()
                .category(categoria)
                .nome("Camiseta preta P")
                .preco(new BigDecimal("150.00"))
                .ativo(true)
                .pesoKg(new BigDecimal("0.3"))
                .alturaCm(new BigDecimal("2"))
                .larguraCm(new BigDecimal("20"))
                .comprimentoCm(new BigDecimal("30"))
                .build());

        // estado pos-checkout: 2 unidades reservadas
        inventoryItemRepository.save(InventoryItem.builder()
                .product(produto).disponivel(10).reservada(2).build());

        Order pedido = Order.builder()
                .user(user)
                .address(Address.builder()
                        .logradouro("Rua das Flores").numero("100").bairro("Centro")
                        .cidade("Sao Paulo").uf("SP").cep("01310100").build())
                .valorItens(new BigDecimal("300.00"))
                .valorFrete(new BigDecimal("25.90"))
                .shippingServiceId(2)
                .build();

        pedido.getItens().add(OrderItem.builder()
                .order(pedido).product(produto)
                .nomeProduto(produto.getNome())
                .precoUnitario(produto.getPreco())
                .quantidade(2)
                .ncm("61091000").cfop("6108").origem("0")
                .build());

        return orderRepository.save(pedido);
    }
}
