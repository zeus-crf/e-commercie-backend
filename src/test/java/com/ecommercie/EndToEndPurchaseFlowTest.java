package com.ecommercie;

import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.UserRepository;
import com.ecommercie.support.DatabaseCleaner;
import com.ecommercie.support.ExternalStubsConfiguration;
import com.ecommercie.support.StubPaymentGateway;
import com.ecommercie.support.StubShippingProvider;
import com.jayway.jsonpath.JsonPath;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.ecommercie.pedido.models.StatusOrder.AGUARDANDO_PAGAMENTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ExternalStubsConfiguration.class})
public class EndToEndPurchaseFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ShippimentRepository shippimentRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;

    private static final String PAYMENT_ID = "9999999999";

    @BeforeEach
    void limpar() {
        databaseCleaner.limparTudo();
    }

    @Test
    void doCatalogoAoEnviado() throws Exception {
        Cookie admin = adminCookie("admin-e2e@test.com");
        Cookie cliente = clienteCookie("cliente-e2e@test.com");

        // 1 - Admin cria categoria e produto
        String categoryId = JsonPath.read(mockMvc.perform(post("/api/v1/admin/catalog/categories")
                .cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                                {"nome":"Camisetas","slug":"camisetas"}
                                """
                ))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(),  "$.data.id");


        String productId = JsonPath.read(mockMvc.perform(post("/api/v1/admin/catalog/products")
                .cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"category_id":"%s","nome":"Camiseta preta P","preco":150.00,
                                 "peso_kg":0.30,"altura_cm":2.00,"largura_cm":20.00,"comprimento_cm":30.00,
                                 "ncm":"61091000","cfop":"6108","origem":"0"}
                                """.formatted(categoryId))
        ).andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        // produto nasce com estoque 0, admin tem que colocar itens
        mockMvc.perform(patch("/api/v1/admin/inventory/" + productId)
                .cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"disponivel": 10}
                        """))
                .andExpect(status().is2xxSuccessful());


        // 2. FRETE - endpoint publico, responde 200 com as cotacoes de do stub
        mockMvc.perform(post("/api/v1/shipping/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cepDestino":"01310100","itens":[
                                  {"pesoKg":0.30,"alturaCm":2.00,"larguraCm":20.00,"comprimentoCm":30.00,"quantidade":2}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].serviceId").value(2))
                .andExpect(jsonPath("$.data[0].price").value(25.90));

        // 3. CARRINHO
        mockMvc.perform(post("/api/v1/cart/items")
                .cookie(cliente).contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"product_id":"%s","quantidade":2}
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtotal").value(300.00));


        String orderId = JsonPath.read(mockMvc.perform(post("/api/v1/orders")
                .cookie(cliente).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"logradouro":"Rua das Flores","numero":"100","bairro":"Centro",
                                                         "cidade":"Sao Paulo","uf":"SP","cep":"01310100",
                                                         "serviceId":2,"valorFrete":25.90}
                        """))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.status").value(AGUARDANDO_PAGAMENTO.name()))
                .andReturn().getResponse().getContentAsString(), "$.data.order_id");


        mockMvc.perform(post("/api/v1/payments/" + orderId + "/preference")
                .cookie(cliente).contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"backUrls":{"success":"http://localhost/ok","failure":"http://localhost/erro",
                                             "pending":"http://localhost/pendente"}}
                                """))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.preferenceId").value(StubPaymentGateway.PREFERENCE_ID_PREFIX + orderId));

        // 6. WEBHOOK — pagamento aprovado chega pelo endpoint publico
        dispararWebhookAprovado(orderId);

        Order pago = orderRepository.findById(orderId).orElseThrow();
        assertThat(pago.getStatus()).isEqualTo(StatusOrder.PAGO);
        assertThat(pago.getMpPaymentId()).isEqualTo(Long.parseLong(PAYMENT_ID));

        // os dois eventos: e-mail de confirmacao e emissao fiscal
        assertThat(outboxEventRepository.findAll())
                .extracting(ev -> ev.getType())
                .containsExactlyInAnyOrder(
                        OutboxTypes.EMAIL_CONFIRMACAO_PEDIDO, OutboxTypes.FISCAL_EMISSAO);

        // 7. ETIQUETA — admin gera; o stub leva o pedido ate ENVIADO
        mockMvc.perform(post("/api/v1/admin/orders/" + orderId + "/label").cookie(admin))
                .andExpect(status().is2xxSuccessful());

        Order enviado = orderRepository.findById(orderId).orElseThrow();
        assertThat(enviado.getStatus()).isEqualTo(StatusOrder.ENVIADO);

        assertThat(shippimentRepository.findAll()).singleElement().satisfies(envio -> {
            assertThat(envio.getTrackingCode()).isEqualTo(StubShippingProvider.TRACKING_CODE);
            assertThat(envio.getServiceId()).isEqualTo(2);
        });
    }

    // ----------------- helpers -----------------

    private void dispararWebhookAprovado(String orderId) throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getExternalReference()).thenReturn(orderId);

        try (MockedConstruction<PaymentClient> ignored = mockConstruction(PaymentClient.class,
                (mockClient, ctx) -> when(mockClient.get(anyLong())).thenReturn(payment))) {

            mockMvc.perform(post("/api/v1/webhooks/mercadopago")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type":"payment","data":{"id":"%s"}}
                                    """.formatted(PAYMENT_ID)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));   // 200 puro, sem envelope ApiResponse
        }
    }

    private Cookie clienteCookie(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Cliente E2E","email":"%s","senha":"senha123","cpf_cnpj":"12345678909"}
                                """.formatted(email)))
                .andReturn().getResponse().getCookie("access_token");
    }

    private Cookie adminCookie(String email) throws Exception {
        userRepository.save(User.builder()
                .nome("Admin E2E").email(email)
                .senha(passwordEncoder.encode("senha123"))
                .cpfCnpj("00000000000").papel(Papel.ADMIN).ativo(true)
                .build());

        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha123"}
                                """.formatted(email)))
                .andReturn().getResponse().getCookie("access_token");
    }
}