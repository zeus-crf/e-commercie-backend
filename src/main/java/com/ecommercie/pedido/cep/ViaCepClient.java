package com.ecommercie.pedido.cep;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Adapter HTTP do ViaCEP (GET /ws/{cep}/json/). Publico e sem token.
 *
 * O ViaCEP responde 200 com {"erro": "true"} para CEP com formato valido que nao existe,
 * e 400 para formato invalido. Os dois viram INEXISTENTE.
 */
@Slf4j
@Service
public class ViaCepClient implements ConsultaCep {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    @Autowired
    public ViaCepClient(@Value("${viacep.base-url:https://viacep.com.br}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).requestFactory(fabricaComTimeout()));
    }

    // usado no teste unitario, com o MockRestServiceServer ligado ao builder
    ViaCepClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    private static SimpleClientHttpRequestFactory fabricaComTimeout() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT);
        fabrica.setReadTimeout(TIMEOUT);
        return fabrica;
    }

    private record ViaCepResponse(String uf, Object erro) {}

    @Override
    public ResultadoCep consultar(String cep) {
        try {
            ViaCepResponse resposta = restClient.get()
                    .uri("/ws/{cep}/json/", cep)
                    .retrieve()
                    .body(ViaCepResponse.class);
            if (resposta == null || resposta.erro() != null || resposta.uf() == null) {
                return ResultadoCep.inexistente();
            }
            return ResultadoCep.encontrado(resposta.uf());
        } catch (HttpClientErrorException.BadRequest e) {
            return ResultadoCep.inexistente();
        } catch (RestClientException e) {
            log.warn("ViaCEP indisponivel ao consultar o CEP {}: {}", cep, e.getMessage());
            return ResultadoCep.indisponivel();
        }
    }
}
