package com.ecommercie.melhor_envio.client;

import com.ecommercie.melhor_envio.GeracaoRecusadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Respostas copiadas do sandbox do Melhor Envio (2026-10-12). O generate real traz uma chave
 * "generate_key" que nao esta no exemplo da doc, e responde 204 sem corpo quando a geracao ja foi pedida.
 */
class MelhorEnvioClientGeracaoTest {

    private static final String ID = "a2f786a0-5091-46d4-af46-de0c8d4fc103";

    private MockRestServiceServer server;
    private MelhorEnvioClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://me.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MelhorEnvioClient(builder);
    }

    @Test
    void gerar_respostaRealComGenerateKey_aceita() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/generate"))
                .andExpect(content().json("{\"orders\":[\"" + ID + "\"]}"))
                .andRespond(withSuccess("""
                        {"generate_key":"a2f79ea2-8eac-487d-9a07-c3931809ee78","%s":{"message":"Envio encaminhado para geração","status":true}}
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.gerarEtiqueta(ID)).doesNotThrowAnyException();
    }

    @Test
    void gerar_204GeracaoJaPedida_aceita() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/generate"))
                .andRespond(withNoContent());

        assertThatCode(() -> client.gerarEtiqueta(ID)).doesNotThrowAnyException();
    }

    @Test
    void gerar_statusFalse_recusaComAMensagemDoMe() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/generate"))
                .andRespond(withSuccess("""
                        {"%s":{"message":"Documento do destinatário inválido","status":false}}
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.gerarEtiqueta(ID))
                .isInstanceOf(GeracaoRecusadaException.class)
                .hasMessage("Documento do destinatário inválido");
    }

    @Test
    void gerar_422_recusaComOCorpoDoMe() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/generate"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"O campo orders.0 selecionado é inválido.\"}"));

        assertThatThrownBy(() -> client.gerarEtiqueta(ID))
                .isInstanceOf(GeracaoRecusadaException.class)
                .hasMessageContaining("orders.0");
    }

    @Test
    void imprimir_devolveOLinkPrivado() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/print"))
                .andExpect(content().json("{\"mode\":\"private\",\"orders\":[\"" + ID + "\"]}"))
                .andRespond(withSuccess("""
                        {"url":"https:\\/\\/sandbox.melhorenvio.com.br\\/portal\\/imprimir\\/x77gURWUpHzo"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.linkImpressao(ID)).isEqualTo("https://sandbox.melhorenvio.com.br/portal/imprimir/x77gURWUpHzo");
    }
}
