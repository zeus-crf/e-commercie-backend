package com.ecommercie.melhor_envio.client;

import com.ecommercie.melhor_envio.CancelamentoRecusadoException;
import com.ecommercie.melhor_envio.dto.Cancelabilidade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;


public class MelhorEnvioClientCancelamentoTest {

    private static final String ID = "a30b7f89-30fb-4a46-88cc-5b4682cf3999";

    private MockRestServiceServer server;
    private MelhorEnvioClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://me.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MelhorEnvioClient(builder);
    }

    @Test
    void cancellable_naoCancelavel_comOMotivoDoMe() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/cancellable"))
                .andExpect(content().json("{\"orders\":[\"" + ID + "\"]}"))
                .andRespond(withSuccess("""
                        {"%s":{"cancellable":false,"uncancellable_reason":"Coleta ja agendada","time":null}}
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        assertThat(client.consultarCancelamento(ID)).isEqualTo(new Cancelabilidade(false, "Coleta ja agendada"));
    }

    @Test
    void cancellable_cancelavel() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/cancellable"))
                .andRespond(withSuccess("""
                        {"%s":{"cancellable":true,"time":0}}
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        assertThat(client.consultarCancelamento(ID).cancelavel()).isTrue();
    }

    @Test
    void cancel_mandaReasonId2_eAceitaCanceledTrue() {
        server.expect(requestTo("https://me.test/api/v2/me/shipment/cancel"))
                .andExpect(content().json("{\"order\":{\"id\":\"" + ID + "\",\"reason_id\":\"2\",\"description\":\"Endereco errado\"}}"))
                .andRespond(withSuccess("""
                        {"%s":{"canceled":true}}
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.cancelarEtiqueta(ID, "Endereco errado")).doesNotThrowAnyException();
    }

    @Test
    void cancel_422_recusaComOCorpoDoMe(){
        server.expect(requestTo("https://me.test/api/v2/me/shipment/cancel"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"A etiqueta nao pode ser cancelada.\"}"));

        assertThatThrownBy(() -> client.cancelarEtiqueta(ID, "X"))
                .isInstanceOf(CancelamentoRecusadoException.class)
                .hasMessageContaining("nao pode ser cancelada");
    }

}
