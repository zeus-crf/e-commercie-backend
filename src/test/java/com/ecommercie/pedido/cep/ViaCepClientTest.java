package com.ecommercie.pedido.cep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Respostas copiadas do ViaCEP real (consultado em 2026-10-12): CEP existente, CEP de 8 digitos
 * que nao existe ({"erro": "true"} com 200) e formato invalido (400).
 */
class ViaCepClientTest {

    private MockRestServiceServer server;
    private ViaCepClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://viacep.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ViaCepClient(builder);
    }

    @Test
    void cepExistente_devolveAUf() {
        server.expect(requestTo("https://viacep.test/ws/25240120/json/"))
                .andRespond(withSuccess("""
                        {"cep": "25240-120", "logradouro": "Rua Serafim Lopes", "localidade": "Duque de Caxias", "uf": "RJ"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.consultar("25240120")).isEqualTo(ResultadoCep.encontrado("RJ"));
    }

    @Test
    void cepQueNaoExiste_viraInexistente() {
        server.expect(requestTo("https://viacep.test/ws/25673726/json/"))
                .andRespond(withSuccess("""
                        {"erro": "true"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.consultar("25673726")).isEqualTo(ResultadoCep.inexistente());
    }

    @Test
    void formatoInvalido_viraInexistente() {
        server.expect(requestTo("https://viacep.test/ws/123/json/"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.consultar("123")).isEqualTo(ResultadoCep.inexistente());
    }

    @Test
    void viaCepFora_viraIndisponivel() {
        server.expect(requestTo("https://viacep.test/ws/25240120/json/"))
                .andRespond(withServerError());

        assertThat(client.consultar("25240120")).isEqualTo(ResultadoCep.indisponivel());
    }
}
