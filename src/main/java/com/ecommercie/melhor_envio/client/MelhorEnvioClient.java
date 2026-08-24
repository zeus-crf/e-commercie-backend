package com.ecommercie.melhor_envio.client;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.*;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.pedido.models.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Service

public class MelhorEnvioClient implements ShippingProvider {

    @Value("${melhorenvio.origin-cep}")
    private String from;

    private final RestClient restClient;

    public MelhorEnvioClient(
            @Value("${melhorenvio.base-url}") String baseUrl,
            @Value("${melhorenvio.token}") String token,
            @Value("${melhorenvio.user-agent}") String userAgent
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", token)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }


    @Override
    public List<ShippingQuote> quote(ShippingQuoteRequest request) {
        List<CalculateItem> products = request.itens().stream()
                .map(i -> CalculateItem.builder()
                        .weight(i.pesoKg())
                        .width(i.larguraCm())
                        .height(i.alturaCm())
                        .length(i.comprimentoCm())
                        .quantity(i.quantidade())
                        .build())
                .toList();

        CalculePayload payload = new CalculePayload(
                new CalculePayload.PostalCode(from),
                new CalculePayload.PostalCode(request.cepDestino()),
                products
        );

        List<MeCalculateResponse> raw = restClient.post()
                .uri("/api/v1/me/shipment/calculate")
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
    public Shipment buyLabel(Order order, int serviceId) {
        return null;
    }

    @Override
    public void cancelLabel(Shipment shipment) {

    }
}
