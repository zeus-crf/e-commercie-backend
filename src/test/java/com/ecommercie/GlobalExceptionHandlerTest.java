package com.ecommercie;

import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.mercado_pago.PaymentGateway;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.pedido.service.OrderService;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import com.ecommercie.support.DatabaseCleaner;
import com.ecommercie.support.ExternalStubsConfiguration;
import com.ecommercie.support.StubShippingProvider;
import com.jayway.jsonpath.JsonPath;
import com.mercadopago.exceptions.MPException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ExternalStubsConfiguration.class})
public class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    CartRepository cartRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired
    DatabaseCleaner databaseCleaner;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    StubShippingProvider shippingStub;


    @MockitoBean PaymentGateway paymentGateway;


    private static final String ENDERECO = """
            { "logradouro":"Rua A","numero":"10","bairro":"Centro","cidade":"Sao Paulo","uf":"SP","cep":"01000000","serviceId":1,"valorFrete":15.00 }
            """;


    @BeforeEach
    void beforeEach() {
        databaseCleaner.limparTudo();
    }


    @Test
    void deveRetornar_405SeOMetodoForIncorreto() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/{id}/label", "qualquer-id").cookie(adminCookie("admin@test.com")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    void deveRetornar_400JsonMalFormatado() throws Exception {
        mockMvc.perform(post("/api/v1/orders").cookie(clienteCookie("email@test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        json mal formatado
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JSON inválido ou corpo da requisição ilegível/errado"));
    }

    @Test
    void deveRetorna_400ParametroInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders?status=XYZ")
                        .cookie(adminCookie("test@gmail")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parâmetro com tipo ou formato inválido"));
    }

    @Test
    void deveRetornar_503QuandoOMeEstaIndisponivel() throws Exception {
        shippingStub.falharNaCotacao(new ResourceAccessException("stub: ME fora do ar"));

        mockMvc.perform(post("/api/v1/shipping/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cepDestino\":\"01310100\",\"itens\":[]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Serviço externo indisponível. Tente novamente mais tarde"));
    }

    @Test
    void deveRetornar_502QuandoErroNaEmUmServicoExterno() throws  Exception {
        shippingStub.falharNaCotacao(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "erro",
                HttpHeaders.EMPTY, "{\"error\":\"falha no ME\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/v1/shipping/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cepDestino\":\"01310100\",\"itens\":[]}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("O serviço externo falhou: ")));
    }

    @Test
    void deveRetornar_503QuandoOMercadoPagoEstaIndisponivel() throws Exception {
        String orderId = pedidoPago();
        Cookie cli = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cli@test.com\",\"senha\":\"senha123\"}"))
                .andReturn().getResponse().getCookie("access_token");
        when(paymentGateway.criarPreferencia(any(), any())).thenThrow(new MPException("timeout"));

        mockMvc.perform(post("/api/v1/payments/{id}/preference", orderId).cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"backUrls":{"success":"http://localhost/ok","failure":"http://localhost/erro","pending":"http://localhost/pendente"}}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Mercado Pago indisponível. Tente novamente mais tarde."));
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
    private Cookie clienteCookie(String email) throws Exception {
        String body = """
                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678900" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
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

    private String pedidoPago() throws Exception {
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque("Camisa", "10", 10);
        mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"%s\",\"quantidade\":2}".formatted(p.getId())))
                .andExpect(status().isOk());
        String json = mockMvc.perform(post("/api/v1/orders").cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON).content(ENDERECO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(json, "$.data.order_id");

        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markPaid();
        orderRepository.save(order);
        return orderId;
    }

}
