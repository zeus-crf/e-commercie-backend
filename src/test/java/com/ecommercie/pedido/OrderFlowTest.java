package com.ecommercie.pedido;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.pedido.service.OrderService;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do pedido (Fase 6).
 *
 * Cobre: checkout reserva estoque e nasce AGUARDANDO_PAGAMENTO; snapshot congela
 * nome/preço; transição inválida vira erro; IDOR (cliente não vê/cancela pedido de
 * outro); e o job de expiração cancela + devolve a reserva (com tempo controlado).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired CartRepository cartRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String ENDERECO = """
            { "logradouro":"Rua A","numero":"10","bairro":"Centro","cidade":"Sao Paulo","uf":"SP","cep":"01000000" }
            """;

    // limpa na ordem das FKs: pedido (cascade item+endereco) e carrinho antes de produtos/usuarios
    @BeforeEach
    void limpar() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----------------- helpers -----------------

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
                .nome("Chefe").email(email)
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

    private Product produtoComEstoque(String nome, String preco, int estoque) {
        Category cat = categoryRepository.save(
                Category.builder().nome("Cat " + nome).slug("cat-" + nome.toLowerCase()).build());
        Product product = productRepository.save(Product.builder()
                .category(cat).nome(nome).preco(new BigDecimal(preco)).ativo(true)
                .pesoKg(new BigDecimal("0.3")).alturaCm(new BigDecimal("2"))
                .larguraCm(new BigDecimal("20")).comprimentoCm(new BigDecimal("30"))
                .build());
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product).disponivel(estoque).reservada(0).build());
        return product;
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

    // ----------------- segurança -----------------

    @Test
    void checkout_semLogin_retorna401() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(ENDERECO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkout_carrinhoVazio_retorna400() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        mockMvc.perform(post("/api/v1/orders").cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON).content(ENDERECO))
                .andExpect(status().isBadRequest());
    }

    // ----------------- checkout -----------------

    @Test
    void checkout_reservaEstoque_eNasceAguardandoPagamento() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        Product p = produtoComEstoque("Camiseta", "100.00", 10);
        addAoCarrinho(cli, p.getId(), 3);

        mockMvc.perform(post("/api/v1/orders").cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON).content(ENDERECO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.data.itens.length()").value(1))
                .andExpect(jsonPath("$.data.itens[0].quantidade").value(3))
                .andExpect(jsonPath("$.data.valorItens").value(300.00))   // 3 * 100
                .andExpect(jsonPath("$.data.endereco.cidade").value("Sao Paulo"));

        // estoque foi RESERVADO, não baixado
        var inv = inventoryItemRepository.findByProductId(p.getId()).orElseThrow();
        assertThat(inv.getReservada()).isEqualTo(3);
        assertThat(inv.getDisponivel()).isEqualTo(10);   // físico intacto

        // carrinho esvaziado
        mockMvc.perform(get("/api/v1/cart").cookie(cli))
                .andExpect(jsonPath("$.data.itens.length()").value(0));
    }

    @Test
    void checkout_congelaSnapshot_precoNaoMudaComOProduto() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        Product p = produtoComEstoque("Camiseta", "100.00", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);

        // admin muda o produto DEPOIS da compra
        Product prod = productRepository.findById(p.getId()).orElseThrow();
        prod.setNome("Outro Nome");
        prod.setPreco(new BigDecimal("999.00"));
        productRepository.save(prod);

        // o pedido continua com o snapshot (nome/preço da hora da compra)
        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cli))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itens[0].nomeProduto").value("Camiseta"))
                .andExpect(jsonPath("$.data.itens[0].precoUnitario").value(100.00));
    }

    // ----------------- transições / IDOR -----------------

    @Test
    void transicaoInvalida_entregarPedidoNaoEnviado_retornaErro() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        Cookie admin = adminCookie("admin@test.com");
        Product p = produtoComEstoque("Camiseta", "50.00", 10);
        addAoCarrinho(cli, p.getId(), 1);
        String orderId = checkout(cli);

        // pedido está AGUARDANDO_PAGAMENTO; pular direto para ENTREGUE é inválido
        mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/entregar").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoDeOutroCliente_naoEhVisivelNemCancelavel() throws Exception {
        Cookie clienteA = clienteCookie("a@test.com");
        Cookie clienteB = clienteCookie("b@test.com");
        Product p = produtoComEstoque("Camiseta", "50.00", 10);
        addAoCarrinho(clienteA, p.getId(), 1);
        String orderIdA = checkout(clienteA);

        // B não vê o pedido de A
        mockMvc.perform(get("/api/v1/orders/" + orderIdA).cookie(clienteB))
                .andExpect(status().isNotFound());

        // B não cancela o pedido de A
        mockMvc.perform(patch("/api/v1/orders/" + orderIdA + "/cancelar").cookie(clienteB))
                .andExpect(status().isNotFound());
    }

    // ----------------- job de expiração -----------------

    @Test
    void expiracao_cancelaPedidoVencido_eDevolveReserva() throws Exception {
        Cookie cli = clienteCookie("a@test.com");
        Product p = produtoComEstoque("Camiseta", "50.00", 10);
        addAoCarrinho(cli, p.getId(), 2);
        String orderId = checkout(cli);

        // força o vencimento: joga o expiresAt para o passado
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(order);

        // dispara o que o job faria
        orderService.expirarPedidosVencidos();

        // pedido cancelado e reserva devolvida
        Order depois = orderRepository.findById(orderId).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusOrder.CANCELADO);

        var inv = inventoryItemRepository.findByProductId(p.getId()).orElseThrow();
        assertThat(inv.getReservada()).isZero();          // reserva liberada
        assertThat(inv.getDisponivel()).isEqualTo(10);    // físico nunca saiu
    }
}
