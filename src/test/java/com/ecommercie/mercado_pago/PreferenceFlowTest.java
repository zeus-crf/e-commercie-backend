package com.ecommercie.mercado_pago;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.support.CatalogoDeTeste;
import com.ecommercie.support.DatabaseCleaner;
import com.ecommercie.support.ExternalStubsConfiguration;
import com.ecommercie.support.StubPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preferencia de pagamento: o pedido e lido e validado numa transacao curta, e o Mercado Pago
 * e chamado depois, fora dela, so com o retrato dos dados (PreferenciaRequest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ExternalStubsConfiguration.class})
class PreferenceFlowTest {

    private static final String ENDERECO = """
            { "logradouro":"Rua A","numero":"10","bairro":"Centro","cidade":"Sao Paulo","uf":"SP","cep":"01000000","serviceId":1,"valorFrete":15.00 }
            """;
    private static final String BACK_URLS = """
            {"backUrls":{"success":"http://localhost/ok","failure":"http://localhost/erro","pending":"http://localhost/pendente"}}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired DatabaseCleaner databaseCleaner;
    @Autowired CatalogoDeTeste catalogo;
    @Autowired StubPaymentGateway paymentStub;

    @BeforeEach
    void limpar() {
        databaseCleaner.limparTudo();
    }

    @Test
    void preferencia_chamaOMercadoPagoForaDeTransacao_comORetratoDoPedido() throws Exception {
        Cookie cli = clienteCookie("cli@test.com");
        String orderId = pedido(cli);

        criarPreferencia(orderId, cli)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferenceId").value(StubPaymentGateway.PREFERENCE_ID_PREFIX + orderId));

        assertThat(paymentStub.transacaoAtivaNasChamadas()).containsExactly(false);
        assertThat(paymentStub.preferencias()).singleElement().satisfies(req -> {
            assertThat(req.orderId()).isEqualTo(orderId);
            assertThat(req.compradorEmail()).isEqualTo("cli@test.com");
            assertThat(req.backUrls().success()).isEqualTo("http://localhost/ok");
            assertThat(req.itens()).singleElement().satisfies(item -> {
                assertThat(item.nome()).isEqualTo("Camiseta");
                assertThat(item.descricao()).isEqualTo("Camiseta de algodao");
                assertThat(item.quantidade()).isEqualTo(2);
                assertThat(item.precoUnitario()).isEqualByComparingTo("50.00");
            });
        });
    }

    @Test
    void pedidoJaPago_recusaSemChamarOMercadoPago() throws Exception {
        Cookie cli = clienteCookie("cli@test.com");
        String orderId = pedido(cli);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markPaid();
        orderRepository.save(order);

        criarPreferencia(orderId, cli)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("não está aguardando pagamento")));

        assertThat(paymentStub.preferencias()).isEmpty();
    }

    @Test
    void pedidoCancelado_recusaSemChamarOMercadoPago() throws Exception {
        Cookie cli = clienteCookie("cli@test.com");
        String orderId = pedido(cli);
        mockMvc.perform(patch("/api/v1/orders/{id}/cancelar", orderId).cookie(cli))
                .andExpect(status().isOk());

        criarPreferencia(orderId, cli)
                .andExpect(status().isUnprocessableEntity());

        assertThat(paymentStub.preferencias()).isEmpty();
    }

    @Test
    void pedidoDeOutroCliente_recusaSemChamarOMercadoPago() throws Exception {
        Cookie dono = clienteCookie("dono@test.com");
        String orderId = pedido(dono);
        Cookie outro = clienteCookie("outro@test.com");

        criarPreferencia(orderId, outro)
                .andExpect(status().isForbidden());

        assertThat(paymentStub.preferencias()).isEmpty();
    }

    // ----------------- helpers -----------------

    private ResultActions criarPreferencia(String orderId, Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/{id}/preference", orderId).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(BACK_URLS));
    }

    private Cookie clienteCookie(String email) throws Exception {
        String body = """
                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678909" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getCookie("access_token");
    }

    /** Pedido AGUARDANDO_PAGAMENTO com 2 camisetas de R$ 50. */
    private String pedido(Cookie cli) throws Exception {
        var p = catalogo.produto("Camiseta", "Camiseta de algodao", "50.00", 10, 0);

        mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variant_id\":\"%s\",\"quantidade\":2}".formatted(p.variantId())))
                .andExpect(status().isOk());
        String json = mockMvc.perform(post("/api/v1/orders").cookie(cli)
                        .contentType(MediaType.APPLICATION_JSON).content(ENDERECO))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.data.order_id");
    }
}
