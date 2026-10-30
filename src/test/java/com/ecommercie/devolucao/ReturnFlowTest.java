package com.ecommercie.devolucao;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.pedido.cep.ConsultaCep;
import com.ecommercie.pedido.cep.ResultadoCep;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.outbox.service.EmailService;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPException;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.Cookie;
import com.ecommercie.support.CatalogoDeTeste;
import com.ecommercie.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReturnFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired CartRepository cartRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;
    @Autowired CatalogoDeTeste catalogo;

    @MockitoBean EmailService emailService;
    @MockitoBean PaymentRefundClient paymentRefundClient;
    @MockitoBean ConsultaCep consultaCep;

    private static final String ENDERECO = """
            { "logradouro":"Rua A","numero":"10","bairro":"Centro","cidade":"Sao Paulo","uf":"SP","cep":"01000000","serviceId":1,"valorFrete":15.00 }
            """;

    @BeforeEach
    void limpar() {
        databaseCleaner.limparTudo();
        when(consultaCep.consultar(anyString())).thenReturn(ResultadoCep.encontrado("SP"));
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Cookie clienteCookie(String email) throws Exception {
        String body = """
                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678909" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getCookie("access_token");
    }

    private Cookie adminCookie(String email) throws Exception {
        userRepository.save(User.builder()
                .nome("Admin").email(email)
                .senha(passwordEncoder.encode("senha123"))
                .cpfCnpj("00000000000").papel(Papel.ADMIN).ativo(true)
                .build());
        String login = """
                { "email": "%s", "senha": "senha123" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andReturn().getResponse().getCookie("access_token");
    }

    private Product produtoComEstoque(String nome, int estoque) {
        return catalogo.produto(nome, "100.00", estoque).product();
    }

    private void addAoCarrinho(Cookie cookie, String productId, int qtd) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"%s\",\"quantidade\":%d}".formatted(productId, qtd)))
                .andExpect(status().isOk());
    }

    private String checkout(Cookie cookie) throws Exception {
        String json = mockMvc.perform(post("/api/v1/orders").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(ENDERECO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.order_id");
    }

    /** Força o pedido para PAGO e seta mpPaymentId diretamente no banco (simula webhook). */
    private void simularPagamento(String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markPaid();
        order.setMpPaymentId(999L);
        orderRepository.save(order);
    }

    /** Força o pedido para DEVOLUCAO_SOLICITADA diretamente no banco. */
    private void simularDevolucaoSolicitada(String orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markPaid();
        order.setMpPaymentId(999L);
        order.markDevolucaoSolicitada();
        order.setReturnReason("produto com defeito");
        orderRepository.save(order);
    }

    // ─── solicitar devolução ─────────────────────────────────────────────────────

    @Test
    void solicitarDevolucao_pedidoPago_transicionaParaDevolucaoSolicitada() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 2);
        String orderId = checkout(cli);
        simularPagamento(orderId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/return")
                        .cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"produto com defeito\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Devolução solicitada com sucesso"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(StatusOrder.DEVOLUCAO_SOLICITADA);
        assertThat(order.getReturnReason()).isEqualTo("produto com defeito");
        assertThat(order.getReturnRequestedAt()).isNotNull();
    }

    @Test
    void solicitarDevolucao_semLogin_retorna401() throws Exception {
        mockMvc.perform(post("/api/v1/orders/qualquer-id/return")
                        .param("motivo", "motivo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void solicitarDevolucao_pedidoAguardandoPagamento_retorna400() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);
        // pedido nasce AGUARDANDO_PAGAMENTO — devolução inválida

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/return")
                        .cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"arrependimento\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void solicitarDevolucao_pedidoDeOutroCliente_retorna404() throws Exception {
        Cookie clienteA = clienteCookie("a@test.com");
        Cookie clienteB = clienteCookie("b@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(clienteA, p.getId(), 1);
        String orderIdA = checkout(clienteA);
        simularPagamento(orderIdA);

        // B tenta solicitar devolução do pedido de A
        mockMvc.perform(post("/api/v1/orders/" + orderIdA + "/return")
                        .cookie(clienteB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"não é meu\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── confirmar devolução (admin) ─────────────────────────────────────────────

    @Test
    void confirmarDevolucao_estornaEstoqueEMarcaDevolvido() throws Exception {
        Cookie admin = adminCookie("admin@test.com");
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 3);
        String orderId = checkout(cli);

        // simula estoque confirmado (como o webhook faria)
        InventoryItem inv = inventoryItemRepository.findByProductId(p.getId()).orElseThrow();
        inv.setReservada(0);
        inv.setDisponivel(7); // 10 - 3 baixados
        inventoryItemRepository.save(inv);

        simularDevolucaoSolicitada(orderId);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Devolução confirmada e reembolso processado"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(StatusOrder.DEVOLVIDO);
        assertThat(order.getRefundedAt()).isNotNull();

        // estoque foi reabastecido
        InventoryItem invDepois = inventoryItemRepository.findByProductId(p.getId()).orElseThrow();
        assertThat(invDepois.getDisponivel()).isEqualTo(10); // 7 + 3 devolvidos
    }

    @Test
    void confirmarDevolucao_pedidoSemDevolucaoSolicitada_retorna400() throws Exception {
        Cookie admin = adminCookie("admin@test.com");
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);
        simularPagamento(orderId); // pedido está PAGO, não DEVOLUCAO_SOLICITADA

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm")
                        .cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmarDevolucao_semAdmin_retorna403() throws Exception {
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);
        simularDevolucaoSolicitada(orderId);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm")
                        .cookie(cli))
                .andExpect(status().isForbidden());
    }
    @Test
    void confirmarDevolucao_pedidoSemDevolucaoSolicitada_naoEstornaNoMercadoPago() throws Exception {
        Cookie admin = adminCookie("admin@test.com");
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);
        simularPagamento(orderId); // PAGO: o cliente nao pediu devolucao

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm").cookie(admin))
                .andExpect(status().isBadRequest());

        // antes o estorno saia ANTES da checagem de status: dinheiro devolvido de um pedido valido
        verify(paymentRefundClient, never()).refund(anyLong(), any(MPRequestOptions.class));
        verify(paymentRefundClient, never()).refund(anyLong());
    }

    @Test
    void confirmarDevolucao_estornaForaDeTransacao_comChaveDeIdempotenciaDoPedido() throws Exception {
        Cookie admin = adminCookie("admin@test.com");
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);
        simularDevolucaoSolicitada(orderId);
        List<Boolean> transacaoAtiva = new ArrayList<>();
        doAnswer(inv -> {
            transacaoAtiva.add(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(paymentRefundClient).refund(anyLong(), any(MPRequestOptions.class));

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm").cookie(admin))
                .andExpect(status().isOk());

        ArgumentCaptor<MPRequestOptions> opcoes = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(paymentRefundClient).refund(eq(999L), opcoes.capture());
        assertThat(opcoes.getValue().getCustomHeaders()).containsEntry("X-Idempotency-Key", "estorno-" + orderId);
        assertThat(transacaoAtiva).containsExactly(false);
    }

    @Test
    void confirmarDevolucao_mercadoPagoFora_naoMarcaDevolvido_eORetryUsaAMesmaChave() throws Exception {
        Cookie admin = adminCookie("admin@test.com");
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camiseta", 10);
        addAoCarrinho(cli, p.getId(), 2);
        String orderId = checkout(cli);
        simularDevolucaoSolicitada(orderId);
        int disponivelAntes = inventoryItemRepository.findByProductId(p.getId()).orElseThrow().getDisponivel();
        when(paymentRefundClient.refund(anyLong(), any(MPRequestOptions.class)))
                .thenThrow(new MPException("timeout"))
                .thenReturn(null);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm").cookie(admin))
                .andExpect(status().isServiceUnavailable());

        Order depoisDaFalha = orderRepository.findById(orderId).orElseThrow();
        assertThat(depoisDaFalha.getStatus()).isEqualTo(StatusOrder.DEVOLUCAO_SOLICITADA);
        assertThat(depoisDaFalha.getRefundedAt()).isNull();
        assertThat(inventoryItemRepository.findByProductId(p.getId()).orElseThrow().getDisponivel()).isEqualTo(disponivelAntes);

        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/return/confirm").cookie(admin))
                .andExpect(status().isOk());

        ArgumentCaptor<MPRequestOptions> opcoes = ArgumentCaptor.forClass(MPRequestOptions.class);
        verify(paymentRefundClient, org.mockito.Mockito.times(2)).refund(eq(999L), opcoes.capture());
        assertThat(opcoes.getAllValues()).extracting(o -> o.getCustomHeaders().get("X-Idempotency-Key"))
                .containsExactly("estorno-" + orderId, "estorno-" + orderId);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.DEVOLVIDO);
        assertThat(inventoryItemRepository.findByProductId(p.getId()).orElseThrow().getDisponivel()).isEqualTo(disponivelAntes + 2);
    }
}
