package com.ecommercie.fiscal;

import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.pedido.models.Address;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.security.models.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o que o provider ENVIA para a Focus NFe, sem credencial e sem rede externa:
 * um HttpServer do JDK sobe em porta aleatória e captura a requisição.
 */
class FocusNfeFiscalProviderTest {

    private HttpServer server;
    private String baseUrl;

    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private final AtomicReference<String> uriRecebida = new AtomicReference<>();
    private final AtomicReference<String> metodoRecebido = new AtomicReference<>();

    /** Resposta que o stub devolve no POST de emissão; cada teste ajusta antes de emitir. */
    private String respostaBody = "{}";
    private int respostaStatus = 201;

    @BeforeEach
    void subirStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        // GET /v2/{modelo}/{ref} = consulta prévia. 404 = "ainda não emitida".
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                responder(exchange, 404, "{\"status\":\"nao_encontrado\"}");
                return;
            }
            metodoRecebido.set(exchange.getRequestMethod());
            uriRecebida.set(exchange.getRequestURI().toString());
            corpoRecebido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(exchange, respostaStatus, respostaBody);
        });

        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void derrubarStub() {
        server.stop(0);
    }

    private void responder(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private FocusNfeFiscalProvider provider(FiscalDocumentType tipo) {
        var emitente = new FocusNfeFiscalProvider.Emitente(
                "12345678000123", "1234567890", "SP", 1, "102", "07", "07", "2", "0");
        return new FocusNfeFiscalProvider(tipo, "token-de-teste", baseUrl, emitente);
    }

    private Order pedido() {
        User user = User.builder()
                .nome("Fulano de Tal").email("fulano@teste.com").cpfCnpj("123.456.789-09")
                .build();
        user.setId("pedido-user");

        Address endereco = Address.builder()
                .logradouro("Rua das Flores").numero("100").bairro("Centro")
                .cidade("Sao Paulo").uf("SP").cep("01310-100")
                .build();

        Order order = Order.builder()
                .user(user).address(endereco)
                .valorItens(new BigDecimal("300.00")).valorFrete(new BigDecimal("25.90"))
                .build();
        order.setId("pedido-1");

        List<OrderItem> itens = new ArrayList<>();
        itens.add(OrderItem.builder()
                .order(order).nomeProduto("Camiseta preta P")
                .precoUnitario(new BigDecimal("150.00")).quantidade(2)
                .ncm("61091000").cfop("6108").origem("0")
                .build());
        order.setItens(itens);

        return order;
    }

    @Test
    void dce_enviaPayloadNoFormatoDaFocus() {
        respostaStatus = 201;
        respostaBody = """
                {"status":"autorizado","chave":"DCe4126031234567800012399001000000001192101527","ref":"ecommercie-pedido-1"}
                """;

        FiscalDocument doc = provider(FiscalDocumentType.DCE).emitir(pedido());

        assertThat(doc.tipo()).isEqualTo(FiscalDocumentType.DCE);
        assertThat(doc.chave()).isEqualTo("DCe4126031234567800012399001000000001192101527");

        assertThat(metodoRecebido.get()).isEqualTo("POST");
        // a ref TEM que chegar na query string
        assertThat(uriRecebida.get()).isEqualTo("/v2/dce?ref=ecommercie-pedido-1");

        String corpo = corpoRecebido.get();
        assertThat(corpo)
                .contains("\"cnpj_emitente\":\"12345678000123\"")
                .contains("\"tipo_emitente\":\"2\"")
                .contains("\"modalidade_transporte\":\"0\"")
                .contains("\"nome_destinatario\":\"Fulano de Tal\"")
                .contains("\"cpf_destinatario\":\"12345678909\"")   // só dígitos
                .contains("\"cep_destinatario\":\"01310100\"")
                .contains("\"descricao_produto\":\"Camiseta preta P\"")
                .contains("\"codigo_ncm\":\"61091000\"")
                .contains("\"quantidade\":2")
                .contains("\"valor_unitario\":150.00")
                .contains("\"valor_produto\":300.00");

        // nomes antigos, inventados, não podem mais aparecer
        assertThat(corpo)
                .doesNotContain("remetente_nome")
                .doesNotContain("destinatario_nome")
                .doesNotContain("quantdade")
                .doesNotContain("\"descricao\"");
    }

    @Test
    void dce_processandoAutorizacao_lancaParaOOutboxRetentar() {
        respostaStatus = 202;
        respostaBody = "{\"status\":\"processando_autorizacao\",\"ref\":\"ecommercie-pedido-1\"}";

        assertThatThrownBy(() -> provider(FiscalDocumentType.DCE).emitir(pedido()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("processamento");
    }

    @Test
    void dce_rejeitada_lancaComMensagemDaSefaz() {
        respostaStatus = 201;
        respostaBody = "{\"status\":\"erro_autorizacao\",\"mensagem_sefaz\":\"CPF invalido\"}";

        assertThatThrownBy(() -> provider(FiscalDocumentType.DCE).emitir(pedido()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CPF invalido");
    }

    @Test
    void nfe_enviaPayloadComCamposObrigatorios() {
        respostaStatus = 201;
        respostaBody = """
                {"status":"autorizado","chave_nfe":"NFe41190612345678000123550010000000221923094166"}
                """;

        FiscalDocument doc = provider(FiscalDocumentType.NFE).emitir(pedido());

        assertThat(doc.tipo()).isEqualTo(FiscalDocumentType.NFE);
        assertThat(doc.chave()).startsWith("NFe");
        assertThat(uriRecebida.get()).isEqualTo("/v2/nfe?ref=ecommercie-pedido-1");

        assertThat(corpoRecebido.get())
                .contains("\"natureza_operacao\"")
                .contains("\"tipo_documento\":1")
                .contains("\"consumidor_final\":1")
                .contains("\"presenca_comprador\":2")
                .contains("\"indicador_inscricao_estadual_destinatario\":9")
                .contains("\"cnpj_emitente\":\"12345678000123\"")
                .contains("\"items\"")            // NF-e usa "items", em inglês
                .contains("\"icms_situacao_tributaria\":\"102\"")
                .contains("\"local_destino\":1"); // emitente SP, destinatário SP
    }

    @Test
    void nfe_processandoAutorizacao_lanca() {
        respostaStatus = 202;
        respostaBody = "{\"status\":\"processando_autorizacao\"}";

        assertThatThrownBy(() -> provider(FiscalDocumentType.NFE).emitir(pedido()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("processamento");
    }
}