package com.ecommercie.melhor_envio.client;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.dto.*;
import com.ecommercie.melhor_envio.models.Shipment;
import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.models.StatusOrder;
import com.ecommercie.pedido.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service

public class MelhorEnvioClient implements ShippingProvider {

    @Value("${melhorenvio.origin-cep}")
    private String from;

    private final RestClient restClient;

    private final OrderRepository orderRepository;

    private final ShippimentRepository shippimentRepository;
    public MelhorEnvioClient(
            @Value("${melhorenvio.base-url}") String baseUrl,
            @Value("${melhorenvio.token}") String token,
            @Value("${melhorenvio.user-agent}") String userAgent, OrderRepository orderRepository, ShippimentRepository shippimentRepository
    ) {
        this.orderRepository = orderRepository;
        this.shippimentRepository = shippimentRepository;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", token)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json")
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
    @Transactional
    public Shipment buyLabel(Order order, int serviceId) {
         order = orderRepository.findById(order.getId())
                 .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (order.getStatus() == StatusOrder.PAGO) {
            order.markSeparando();
        }

         List<MeCartRequest.MeProduct> products = order.getItens().stream()
                 .map(i -> MeCartRequest.MeProduct.builder()
                         .name(i.getNomeProduto())
                         .quantity(i.getQuantidade())
                         .unitary_value(i.getPrecoUnitario()).build()).toList();

        MeCartRequest.MeAddress address = new MeCartRequest.MeAddress(
                order.getUser().getNome(),
                null,
                order.getUser().getEmail(),
                order.getUser().getCpfCnpj(),
                order.getAddress().getCep(),
                order.getAddress().getLogradouro(),
                order.getAddress().getNumero(),
                order.getAddress().getBairro(),
                order.getAddress().getCidade(),
                order.getAddress().getUf()
        );

        MeCartRequest.MeAddress from = new MeCartRequest.MeAddress(
                "Loja",
                "(21) 99999-9999",
                "loja@gmail.com",
                "943.839.983-73",
                "25240-120",
                "Rua da Loja",   // logradouro
                "10",            // numero
                "Centro",        // bairro
                "Petrópolis",    // cidade
                "RJ"             // UF
        );

        List<MeCartRequest.MeVolume> volumes = new ArrayList<>();

        for (OrderItem item : order.getItens()) {

            MeCartRequest.MeVolume volume = MeCartRequest.MeVolume.builder()
                    .weight(item.getProduct().getPesoKg().multiply(BigDecimal.valueOf(item.getQuantidade())))
                    .width(item.getProduct().getLarguraCm())
                    .height(item.getProduct().getAlturaCm())
                    .length(item.getProduct().getComprimentoCm()).build();

            volumes.add(volume);
        }

        MeCartRequest requestCart = new MeCartRequest(
                serviceId,
                from,
                address,
                products,
                volumes
                );

        MeCartResponse cartResponse = restClient.post()
                .uri("/api/v1/me/cart")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestCart)
                .retrieve()
                .body(MeCartResponse.class);

        restClient.post()
                .uri("/api/v1/me/shipment/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MeCheckoutRequest(List.of(cartResponse.id())))
                .retrieve()
                .toBodilessEntity();

        Shipment shipment = shippimentRepository.save(Shipment.builder()
                .order(order)
                .meOrderId(cartResponse.id())
                .serviceId(serviceId)
                .labelGeneratedAt(java.time.LocalDateTime.now())
                .build());

        order.markEnviando();

        return shipment;
    }

    @Override
    public void cancelLabel(Shipment shipment) {

    }
}
