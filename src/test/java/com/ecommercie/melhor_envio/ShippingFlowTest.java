package com.ecommercie.melhor_envio;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.melhor_envio.repository.ShipmentTrackingEventRepository;
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
import com.ecommercie.support.StubShippingProvider;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Frete: geração de etiqueta (orquestrada pelo ShippingService) e webhook de rastreio.
 * O Melhor Envio é o StubShippingProvider (ExternalStubsConfiguration).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, ExternalStubsConfiguration.class})
class ShippingFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired OrderRepository orderRepository;
    @Autowired ShippimentRepository shippimentRepository;
    @Autowired ShipmentTrackingEventRepository shipmentTrackingEventRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;
    @Autowired StubShippingProvider shippingStub;

    private static final String ENDERECO = """
            { "logradouro":"Rua A","numero":"10","bairro":"Centro","cidade":"Sao Paulo","uf":"SP","cep":"01000000","serviceId":2,"valorFrete":15.00 }
            """;

    @BeforeEach
    void limpar() {
        databaseCleaner.limparTudo();
    }

    // ----------------- etiqueta -----------------

    @Test
    void gerarEtiqueta_levaPagoAteEnviado_gravaEnvio_eAvisaCliente() throws Exception {
        String orderId = pedidoPago();

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isOk());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.ENVIADO);
        assertThat(shippimentRepository.findAll()).singleElement().satisfies(envio -> {
            assertThat(envio.getMeOrderId()).isEqualTo(StubShippingProvider.ME_ORDER_PREFIX + orderId);
            assertThat(envio.getServiceId()).isEqualTo(2);
            assertThat(envio.getLabelGeneratedAt()).isNotNull();
        });
        assertThat(outboxEventRepository.findAll())
                .extracting(ev -> ev.getType())
                .filteredOn(OutboxTypes.EMAIL_PEDIDO_ENVIADO::equals)
                .hasSize(1);

        // o client recebeu um snapshot com os dados do pedido
        assertThat(shippingStub.carrinhos()).singleElement().satisfies(req -> {
            assertThat(req.orderId()).isEqualTo(orderId);
            assertThat(req.serviceId()).isEqualTo(2);
            assertThat(req.destinatario().email()).isEqualTo("cli@test.com");
            assertThat(req.destinatario().cep()).isEqualTo("01000000");
            assertThat(req.destinatario().logradouro()).isEqualTo("Rua A");
            assertThat(req.itens()).singleElement().satisfies(item -> {
                assertThat(item.quantidade()).isEqualTo(2);
                assertThat(item.precoUnitario()).isEqualByComparingTo("50.00");
                assertThat(item.pesoKg()).isEqualByComparingTo("0.3");
                assertThat(item.comprimentoCm()).isEqualByComparingTo("30");
            });
            assertThat(req.documentoFiscal()).isNull();   // fiscal desligado nos testes
        });
        assertThat(shippingStub.compras()).containsExactly(StubShippingProvider.ME_ORDER_PREFIX + orderId);
    }

    @Test
    void gerarEtiqueta_pedidoNaoPago_recusaSemChamarOMelhorEnvio() throws Exception {
        String orderId = pedidoPago();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markSeparando();
        order.markEnviando();                  // ja ENVIADO: nao pode gerar etiqueta de novo
        orderRepository.save(order);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isUnprocessableEntity());

        assertThat(shippingStub.carrinhos()).isEmpty();
        assertThat(shippingStub.compras()).isEmpty();
    }

    @Test
    void gerarEtiqueta_chamaOMelhorEnvioForaDeTransacao() throws Exception {
        String orderId = pedidoPago();

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isOk());

        // carrinho + compra: nenhuma das duas chamadas HTTP pode segurar transacao do banco
        assertThat(shippingStub.transacaoAtivaNasChamadas()).containsExactly(false, false);
    }



    @Test
    void compraRecusadaPeloMe_descartaPendente_eRetryCriaCarrinhoNovo() throws Exception {

        String orderId = pedidoPago();
        Cookie admin = adminCookie();
        shippingStub.falharNaCompra(true); // 422: o ME recusou o não cobrou

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isUnprocessableEntity());


        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.EM_SEPARACAO);
        assertThat(shippimentRepository.findAll()).isEmpty();
        assertThat(outboxEventRepository.findAll()).extracting(ev -> ev.getType())
                .doesNotContain(OutboxTypes.EMAIL_PEDIDO_ENVIADO);

        shippingStub.falharNaCompra(false);
        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isOk());

        assertThat(shippingStub.carrinhos()).hasSize(2);
        assertThat(shippingStub.compras()).hasSize(1);

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.ENVIADO);
        assertThat(outboxEventRepository.findAll()).extracting(ev -> ev.getType())
                .filteredOn(OutboxTypes.EMAIL_PEDIDO_ENVIADO::equals)
                .hasSize(1);


    }

    @Test
    void cliqueDuplo_checkoutJaReservado_recebe409SemChamarOMe() throws Exception {
        String orderId = pedidoSeparadoComEnvioPendente("me-pendente-1", LocalDateTime.now());

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isConflict());

        assertThat(shippingStub.transacaoAtivaNasChamadas()).isEmpty();   // nenhuma chamada ao ME
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.EM_SEPARACAO);
    }

    @Test
    void reservaExpirada_retryReaproveitaCarrinho_eConclui() throws Exception {
        String orderId = pedidoSeparadoComEnvioPendente("me-pendente-2", LocalDateTime.now().minusMinutes(10));

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isOk());

        assertThat(shippingStub.carrinhos()).isEmpty();
        assertThat(shippingStub.compras()).containsExactly("me-pendente-2");
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.ENVIADO);
    }

    @Test
    void corridaAoGravarPendente_segundaRequisicaoRecebe409SemPagar() throws Exception {
        String orderId = pedidoPago();
        // outra requisicao grava o envio deste pedido enquanto esta ainda criava o carrinho
        shippingStub.aoCriarCarrinho(() -> shippimentRepository.save(Shipment.builder()
                .order(orderRepository.findById(orderId).orElseThrow())
                .meOrderId("carrinho-da-outra-requisicao")
                .serviceId(2)
                .checkoutIniciadoEm(LocalDateTime.now())
                .build()));

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isConflict());

        assertThat(shippingStub.compras()).isEmpty();
        assertThat(shippimentRepository.findAll()).singleElement()
                .satisfies(envio -> assertThat(envio.getMeOrderId()).isEqualTo("carrinho-da-outra-requisicao"));
    }

    @Test
    void compraSemResposta_mantemPendenteReservado_eBloqueiaRetryImediato() throws Exception {
        String orderId = pedidoPago();
        Cookie admin = adminCookie();
        shippingStub.falharNaCompraSemResposta(true);   // timeout: nao se sabe se cobrou

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isConflict());

        assertThat(shippimentRepository.findAll()).singleElement().satisfies(envio -> {
            assertThat(envio.getLabelGeneratedAt()).isNull();
            assertThat(envio.getCheckoutIniciadoEm()).isNotNull();
        });

        // retry imediato: a reserva bloqueia, sem nenhuma chamada nova ao ME
        shippingStub.falharNaCompraSemResposta(false);
        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isConflict());

        assertThat(shippingStub.transacaoAtivaNasChamadas()).hasSize(2);   // so carrinho + 1 compra
        assertThat(shippingStub.compras()).isEmpty();
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.EM_SEPARACAO);
    }

    @Test
    void falhaNoCarrinho_naoGravaEnvio_eRetryFunciona() throws Exception {
        String orderId = pedidoPago();
        Cookie admin = adminCookie();
        shippingStub.falharNoCarrinho(true);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isUnprocessableEntity());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.EM_SEPARACAO);
        assertThat(shippimentRepository.findAll()).isEmpty();
        assertThat(shippingStub.compras()).isEmpty();

        shippingStub.falharNoCarrinho(false);
        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isOk());

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.ENVIADO);
        assertThat(shippimentRepository.findAll()).hasSize(1);
    }

    // ----------------- rastreio -----------------

    @Test
    void rastreioEntregue_marcaEntregue_eAvisaUmaVezSo() throws Exception {
        String orderId = pedidoPago();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markSeparando();
        order.markEnviando();
        orderRepository.save(order);
        envioGravado(orderId, "me-rastreio-1");

        rastreio("me-rastreio-1", "delivered");
        rastreio("me-rastreio-1", "delivered");   // o ME reenvia

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.ENTREGUE);
        assertThat(outboxEventRepository.findAll())
                .extracting(ev -> ev.getType())
                .filteredOn(OutboxTypes.EMAIL_PEDIDO_ENTREGUE::equals)
                .hasSize(1);
        assertThat(shipmentTrackingEventRepository.findAll()).hasSize(2);
    }

    @Test
    void rastreioEntregue_pedidoAindaNaoEnviado_naoMudaStatusNemAvisa() throws Exception {
        String orderId = pedidoPago();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markSeparando();
        orderRepository.save(order);
        envioGravado(orderId, "me-rastreio-2");

        rastreio("me-rastreio-2", "delivered");

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.EM_SEPARACAO);
        assertThat(outboxEventRepository.findAll())
                .extracting(ev -> ev.getType())
                .doesNotContain(OutboxTypes.EMAIL_PEDIDO_ENTREGUE);
        assertThat(shipmentTrackingEventRepository.findAll()).hasSize(1);
        Shipment shipment = shippimentRepository.findByMeOrderId("me-rastreio-2").orElseThrow();
        assertThat(shipment.getTrackingStatus()).isEqualTo("delivered");
        assertThat(shipment.getDeliveredAt()).isNotNull();
    }


    @Test
    void meCobrouMasARespostaSePerdeu_retryConfereNoMe_eConcluiSemPagarDeNovo() throws Exception {
        String orderId = pedidoPago();
        Cookie admin = adminCookie();
        shippingStub.cobrarMasPerderResposta(true);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isConflict());
        assertThat(shippingStub.compras()).hasSize(1); // o ME cobrou


        expirarReserva(orderId);
        shippingStub.cobrarMasPerderResposta(false);
        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isOk());

        assertThat(shippingStub.compras()).hasSize(1); // NÃO pagou de novo

        assertThat(shippingStub.consultas()).containsExactly(StubShippingProvider.ME_ORDER_PREFIX + orderId);
        assertThat(shippingStub.transacaoAtivaNasChamadas()).containsOnly(false);
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(StatusOrder.ENVIADO);
        assertThat(outboxEventRepository.findAll()).extracting(ev -> ev.getType())
                .filteredOn(OutboxTypes.EMAIL_PEDIDO_ENVIADO::equals)
                .hasSize(1);

    }

    @Test
    void pendenteCanceladoNoMe_descartaECriaEtiquetaNova() throws Exception {
        String orderId = pedidoSeparadoComEnvioPendente("me-cancelado", LocalDateTime.now().minusMinutes(10));
        shippingStub.situacao("me-cancelado", SituacaoEtiqueta.CANCELADA);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(adminCookie()))
                .andExpect(status().isOk());

        assertThat(shippingStub.carrinhos()).hasSize(1);   // etiqueta nova no carrinho do ME
        assertThat(shippingStub.compras()).containsExactly(StubShippingProvider.ME_ORDER_PREFIX + orderId);
        assertThat(shippimentRepository.findAll()).singleElement().satisfies(envio -> {
            assertThat(envio.getMeOrderId()).isEqualTo(StubShippingProvider.ME_ORDER_PREFIX + orderId);
            assertThat(envio.getLabelGeneratedAt()).isNotNull();
        });
    }

    @Test
    void consultaAoMeFalha_naoPaga_eLiberaParaNovaTentativaNaHora() throws Exception {
        String orderId = pedidoSeparadoComEnvioPendente("me-pendente-3", LocalDateTime.now().minusMinutes(10));
        Cookie admin = adminCookie();
        shippingStub.falharNaConsulta(true);

        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isConflict());

        assertThat(shippingStub.compras()).isEmpty();
        assertThat(shippimentRepository.findAll()).singleElement().satisfies(
                envio -> assertThat(envio.getCheckoutIniciadoEm()).isNull());

        shippingStub.falharNaConsulta(false);
        mockMvc.perform(post("/api/v1/admin/orders/{id}/label", orderId).cookie(admin))
                .andExpect(status().isOk());   // sem esperar a janela

        assertThat(shippingStub.compras()).containsExactly("me-pendente-3");
    }

    @Test
    void rastreioSemCodigo_naoApagaOCodigoJaGravado() throws Exception {
        String orderId = pedidoPago();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markSeparando();
        order.markEnviando();
        orderRepository.save(order);
        envioGravado(orderId, "me-rastreio-sem-codigo");

        rastreio("me-rastreio-sem-codigo", "posted");   // chega com tracking BR123
        mockMvc.perform(post("/api/v1/webhooks/melhorenvio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":"order.delivered","data":{"id":"me-rastreio-sem-codigo","status":"delivered","tracking":null}}
                                """))
                .andExpect(status().isOk());

        assertThat(shippimentRepository.findByMeOrderId("me-rastreio-sem-codigo").orElseThrow().getTrackingCode())
                .isEqualTo("BR123");
    }

    // ----------------- helpers -----------------

    private void rastreio(String meOrderId, String statusMe) throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/melhorenvio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event":"order.%s","data":{"id":"%s","status":"%s","tracking":"BR123"}}
                                """.formatted(statusMe, meOrderId, statusMe)))
                .andExpect(status().isOk());
    }

    private void envioGravado(String orderId, String meOrderId) {
        shippimentRepository.save(Shipment.builder()
                .order(orderRepository.findById(orderId).orElseThrow())
                .meOrderId(meOrderId)
                .serviceId(2)
                .labelGeneratedAt(LocalDateTime.now())
                .build());
    }

    /** Simula que a janela de reserva do checkout (5 min) ja passou. */
    private void expirarReserva(String orderId) {
        Shipment envio = shippimentRepository.findByOrderId(orderId).orElseThrow();
        envio.setCheckoutIniciadoEm(LocalDateTime.now().minusMinutes(10));
        shippimentRepository.save(envio);
    }

    /** Cliente compra 2 unidades e o pedido vai direto para PAGO (o webhook do MP é testado no E2E). */
    private String pedidoPago() throws Exception {
        Cookie cli = clienteCookie("cli@test.com");
        Product p = produtoComEstoque();
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

    /** Pedido EM_SEPARACAO com um envio pendente (sem etiqueta) reservado no instante dado. */
    private String pedidoSeparadoComEnvioPendente(String meOrderId, LocalDateTime checkoutIniciadoEm) throws Exception {
        String orderId = pedidoPago();
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.markSeparando();
        orderRepository.save(order);
        shippimentRepository.save(Shipment.builder()
                .order(order)
                .meOrderId(meOrderId)
                .serviceId(2)
                .checkoutIniciadoEm(checkoutIniciadoEm)
                .build());
        return orderId;
    }

    private Cookie clienteCookie(String email) throws Exception {
        String body = """
                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678900" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("access_token");
    }

    private Cookie adminCookie() throws Exception {
        userRepository.save(User.builder()
                .nome("Chefe").email("admin@test.com")
                .senha(passwordEncoder.encode("senha123"))
                .cpfCnpj("00000000000").papel(Papel.ADMIN).ativo(true)
                .build());
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"admin@test.com\", \"senha\": \"senha123\" }"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("access_token");
    }

    private Product produtoComEstoque() {
        Category cat = categoryRepository.save(Category.builder().nome("Camisetas").slug("camisetas").build());
        Product product = productRepository.save(Product.builder()
                .category(cat).nome("Camiseta").preco(new BigDecimal("50.00")).ativo(true)
                .pesoKg(new BigDecimal("0.3")).alturaCm(new BigDecimal("2"))
                .larguraCm(new BigDecimal("20")).comprimentoCm(new BigDecimal("30"))
                .build());
        inventoryItemRepository.save(InventoryItem.builder()
                .product(product).disponivel(10).reservada(0).build());
        return product;
    }
}
