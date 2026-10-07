package com.ecommercie.melhor_envio.client;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.*;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
import com.ecommercie.melhor_envio.models.Shipment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

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

    public MelhorEnvioClient(
            @Value("${melhorenvio.base-url}") String baseUrl,
            @Value("${melhorenvio.token}") String token,
            @Value("${melhorenvio.user-agent}") String userAgent
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", token)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public static SituacaoEtiqueta situacaoDo(MeOrderResponse item) {
        if (item == null) {
            return SituacaoEtiqueta.INDEFINIDA;
        }
        if (item.canceledAt() != null || item.expiredAt() != null) {
            return SituacaoEtiqueta.CANCELADA;
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
                products
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

        List<MeCartRequest.MeVolume> volumes = request.itens().stream()
                .map(i -> new MeCartRequest.MeVolume(
                        i.pesoKg().multiply(BigDecimal.valueOf(i.quantidade())),
                        i.larguraCm(), i.alturaCm(), i.comprimentoCm()))
                .toList();

        BigDecimal insuranceValue = request.itens().stream()
                .map(i -> i.precoUnitario().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        MeCartRequest requestCart = new MeCartRequest(
                request.serviceId(),
                remetente,
                to,
                products,
                volumes,
                insuranceValue.compareTo(BigDecimal.ZERO) > 0 ? insuranceValue : null,
                opcoesFiscais(request.documentoFiscal())
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

    @Override
    public void cancelLabel(Shipment shipment) {

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
    private MeCartRequest.MeOptions opcoesFiscais(EtiquetaRequest.DocumentoFiscal documento) {
        if (documento == null) {
            return null;
        }
        return switch (documento.tipo()) {
            case DCE -> new MeCartRequest.MeOptions(null, new MeCartRequest.MeOptions.MeDce(documento.chave()));
            case NFE -> new MeCartRequest.MeOptions(new MeCartRequest.MeOptions.MeInvoice(documento.chave()), null);
            default -> null;
        };
    }
}
