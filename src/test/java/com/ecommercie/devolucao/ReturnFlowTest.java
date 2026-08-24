package com.ecommercie.devolucao;

import com.ecommercie.TestcontainersConfiguration;
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
import jakarta.servlet.http.Cookie;
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

    @MockitoBean EmailService emailService;
    @MockitoBean PaymentRefundClient paymentRefundClient;

    private static final String ENDERECO = """
            { "logradouro":"Rua A","numero":"10","bairro":"Centro","cidade":"Sao Paulo","uf":"SP","cep":"01000000","serviceId":1,"valorFrete":15.00 }
            """;

    @BeforeEach
    void limpar() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Cookie clienteCookie(String email) throws Exception {
        String body = """
                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678900" }
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
        Category cat = categoryRepository.save(
                Category.builder().nome("Cat").slug("cat-" + nome.toLowerCase()).build());
        Product p = productRepository.save(Product.builder()
                .category(cat).nome(nome).preco(new BigDecimal("100.00")).ativo(true)
                .pesoKg(new BigDecimal("0.3")).alturaCm(new BigDecimal("2"))
                .larguraCm(new BigDecimal("20")).comprimentoCm(new BigDecimal("30"))
                .build());
        inventoryItemRepository.save(InventoryItem.builder()
                .product(p).disponivel(estoque).reservada(0).build());
        return p;
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
}
