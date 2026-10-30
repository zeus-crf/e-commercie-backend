package com.ecommercie.melhor_envio.client;

import com.ecommercie.melhor_envio.CancelamentoRecusadoException;
import com.ecommercie.melhor_envio.GeracaoRecusadaException;
import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.*;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
import com.ecommercie.melhor_envio.models.Shipment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Adapter HTTP do Melhor Envio. So monta os payloads e chama a API: nao le nem grava no banco
 * e nao muda status de pedido (isso e do ShippingService / OrderService).
 */
@Slf4j
@Service
public class MelhorEnvioClient implements ShippingProvider {

    @Value("${melhorenvio.origin-cep}")
    private String from;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public MelhorEnvioClient(
            @Value("${melhorenvio.base-url}") String baseUrl,
            @Value("${melhorenvio.token}") String token,
            @Value("${melhorenvio.user-agent}") String userAgent
    ) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", token)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json"));
    }

    // usado no teste unitario, com o MockRestServiceServer ligado ao builder
    MelhorEnvioClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public static SituacaoEtiqueta situacaoDo(MeOrderResponse item) {
        if (item == null) {
            return SituacaoEtiqueta.INDEFINIDA;
        }
        if (item.canceledAt() != null || item.expiredAt() != null) {
            return SituacaoEtiqueta.CANCELADA;
        }
        if (item.generatedAt() != null) {
            return SituacaoEtiqueta.GERADA;
        }
        if (item.paidAt() != null){
            return SituacaoEtiqueta.PAGA;
        }
        if ("pending".equals(item.status())) {
            return SituacaoEtiqueta.PENDENTE_NO_CARRINHO;
        }
        return SituacaoEtiqueta.INDEFINIDA;
    }

    @Override
    public List<ShippingQuote> quote(ShippingQuoteRequest request) {
        List<CalculateItem> products = request.itens().stream()
                .map(i -> new CalculateItem(i.pesoKg(), i.larguraCm(), i.alturaCm(), i.comprimentoCm(), i.quantidade()))
                .toList();

        CalculePayload payload = new CalculePayload(
                new CalculePayload.PostalCode(from),
                new CalculePayload.PostalCode(request.cepDestino()),
                products,
                request.valorSegurado() != null ? new CalculePayload.Options(request.valorSegurado()) : null
        );

        List<MeCalculateResponse> raw = restClient.post()
                .uri("/api/v2/me/shipment/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return raw.stream()
                .filter(r -> r.error() == null)
                .map(r -> new ShippingQuote(
                        r.id(),
                        r.name(),
                        r.company().name(),
                        new BigDecimal(r.price()),
                        r.delivery_time()
                ))
                .toList();
    }

    @Override
    public String adicionarAoCarrinho(EtiquetaRequest request) {
        var d = request.destinatario();
        MeCartRequest.MeAddress to = new MeCartRequest.MeAddress(
                d.nome(), "(11) 99999-9999", d.email(), d.documento(), d.cep(),
                d.logradouro(), d.numero(), d.bairro(), d.cidade(), d.uf(), "BR");

        MeCartRequest.MeAddress remetente = new MeCartRequest.MeAddress(
                "Loja", "(21) 99999-9999", "loja@gmail.com", "111.444.777-35", "25240-120",
                "Rua da Loja", "10", "Centro", "Petrópolis", "RJ", "BR");

        List<MeCartRequest.MeProduct> products = request.itens().stream()
                .map(i -> new MeCartRequest.MeProduct(i.nome(), i.quantidade(), i.precoUnitario()))
                .toList();

        // um volume so: os Correios recusam envio com mais de um (ex.: P e G do mesmo pedido)
        List<MeCartRequest.MeVolume> volumes = List.of(MeCartRequest.MeVolume.agrupando(request.itens()));

        MeCartRequest requestCart = new MeCartRequest(
                request.serviceId(),
                remetente,
                to,
                products,
                volumes,
                opcoes(request)
        );

        try {
            log.info("ME cart payload: {}", objectMapper.writeValueAsString(requestCart));
        } catch (JsonProcessingException e) {
            log.warn("Erro ao serializar payload para log", e);
        }

        MeCartResponse cartResponse = restClient.post()
                .uri("/api/v2/me/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestCart)
                .retrieve()
                .body(MeCartResponse.class);

        return cartResponse.id();
    }

    @Override
    public void finalizarCompra(String meOrderId) {
        restClient.post()
                .uri("/api/v2/me/shipment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MeCheckoutRequest(List.of(meOrderId)))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Formato real (sandbox, 2026-10-12), diferente do exemplo da doc:
     *   200 {"generate_key": "...", "<id>": {"message": "Envio encaminhado para geração", "status": true}}
     *   204 sem corpo quando a geração deste item já tinha sido pedida (retry)
     * A geração é assíncrona: logo depois, a consulta ainda mostra generated_at nulo.
     */
    @Override
    public void gerarEtiqueta(String meOrderId) {
        Map<String, Object> resposta;
        try {
            resposta = restClient.post()
                    .uri("/api/v2/me/shipment/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MeCheckoutRequest(List.of(meOrderId)))   // mesmo corpo do checkout: {"orders": [...]}
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException e) {
            throw new GeracaoRecusadaException(e.getResponseBodyAsString());
        }
        if (resposta == null) {
            return;   // 204: geração já pedida antes
        }
        if (!(resposta.get(meOrderId) instanceof Map<?, ?> resultado)) {
            throw new GeracaoRecusadaException("o Melhor Envio não devolveu o resultado da etiqueta " + meOrderId);
        }
        if (!Boolean.TRUE.equals(resultado.get("status"))) {
            throw new GeracaoRecusadaException(String.valueOf(resultado.get("message")));
        }
    }

    @Override
    public String linkImpressao(String meOrderId) {
        MePrintResponse resposta = restClient.post()
                .uri("/api/v2/me/shipment/print")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MePrintRequest("private", List.of(meOrderId)))
                .retrieve()
                .body(MePrintResponse.class);
        return resposta.url();
    }

    @Override
    public Cancelabilidade consultarCancelamento(String meOrderId) {
        Map<String, Object> resposta = restClient.post()
                .uri("/api/v2/me/shipment/cancellable")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MeCheckoutRequest(List.of(meOrderId)))   // corpo {"orders": [...]}
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (resposta == null || !(resposta.get(meOrderId) instanceof Map<?, ?> item)) {
            return new Cancelabilidade(false, "o Melhor Envio não devolveu a situação da etiqueta");
        }
        Object motivo = item.get("uncancellable_reason");
        return new Cancelabilidade(Boolean.TRUE.equals(item.get("cancellable")), motivo == null ? "" : motivo.toString());
    }

    @Override
    public void cancelarEtiqueta(String meOrderId, String motivo) {
        Map<String, Object> resposta;
        try {
            resposta = restClient.post()
                    .uri("/api/v2/me/shipment/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    // reason_id e sempre "2" para integracoes (doc do ME)
                    .body(Map.of("order", Map.of("id", meOrderId, "reason_id", "2", "description", motivo)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (HttpClientErrorException e) {
            throw new CancelamentoRecusadoException(e.getResponseBodyAsString());
        }
        if (resposta == null || !(resposta.get(meOrderId) instanceof Map<?, ?> item)
                || !Boolean.TRUE.equals(item.get("canceled"))) {
            throw new CancelamentoRecusadoException("o Melhor Envio não confirmou o cancelamento da etiqueta " + meOrderId);
        }
    }

    @Override
    public SituacaoEtiqueta consultarSituacao(String meOrderId) {
        try {
            MeOrderResponse item = restClient.get()
                    .uri("/api/v2/me/orders/{id}", meOrderId)
                    .retrieve()
                    .body(MeOrderResponse.class);
            return situacaoDo(item);
        } catch (HttpClientErrorException.NotFound e) {
            return SituacaoEtiqueta.CANCELADA;   // o item nao existe mais no ME
        }
    }

    // DC-e vai em options.dce.key; NF-e em options.invoice.key.
    private MeCartRequest.MeOptions opcoes(EtiquetaRequest request) {
        BigDecimal seguro = request.valorSegurado();
        BigDecimal valorSegurado = seguro.signum() > 0 ? seguro : null;
        var documento = request.documentoFiscal();

        MeCartRequest.MeOptions.MeInvoice invoice = null;
        MeCartRequest.MeOptions.MeDce dce = null;
        if (documento != null) {
            switch (documento.tipo()) {
                case DCE -> dce = new MeCartRequest.MeOptions.MeDce(documento.chave());
                case NFE -> invoice = new MeCartRequest.MeOptions.MeInvoice(documento.chave());
                default -> { }
            }
        }
        if (valorSegurado == null && invoice == null && dce == null) {
            return null;
        }
        return new MeCartRequest.MeOptions(valorSegurado, invoice, dce);
    }
}
