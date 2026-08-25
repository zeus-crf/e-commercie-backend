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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MelhorEnvioClient implements ShippingProvider {

    @Value("${melhorenvio.origin-cep}")
    private String from;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OrderRepository orderRepository;
    private final ShippimentRepository shippimentRepository;

    public MelhorEnvioClient(
            @Value("${melhorenvio.base-url}") String baseUrl,
            @Value("${melhorenvio.token}") String token,
            @Value("${melhorenvio.user-agent}") String userAgent,
            OrderRepository orderRepository,
            ShippimentRepository shippimentRepository
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
    @Transactional
    public Shipment buyLabel(Order order, int serviceId) {
         order = orderRepository.findById(order.getId())
                 .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        if (order.getStatus() == StatusOrder.PAGO) {
            order.markSeparando();
        }

         List<MeCartRequest.MeProduct> products = order.getItens().stream()
                 .map(i -> new MeCartRequest.MeProduct(i.getNomeProduto(), i.getQuantidade(), i.getPrecoUnitario()))
                 .toList();

        MeCartRequest.MeAddress address = new MeCartRequest.MeAddress(
                order.getUser().getNome(),
                "(11) 99999-9999",
                order.getUser().getEmail(),
                order.getUser().getCpfCnpj(),
                order.getAddress().getCep(),
                order.getAddress().getLogradouro(),
                order.getAddress().getNumero(),
                order.getAddress().getBairro(),
                order.getAddress().getCidade(),
                order.getAddress().getUf(),
                "BR"
        );

        MeCartRequest.MeAddress from = new MeCartRequest.MeAddress(
                "Loja",
                "(21) 99999-9999",
                "loja@gmail.com",
                "111.444.777-35",
                "25240-120",
                "Rua da Loja",
                "10",
                "Centro",
                "Petrópolis",
                "RJ",
                "BR"
        );

        List<MeCartRequest.MeVolume> volumes = new ArrayList<>();

        for (OrderItem item : order.getItens()) {

            MeCartRequest.MeVolume volume = new MeCartRequest.MeVolume(
                    item.getProduct().getPesoKg().multiply(BigDecimal.valueOf(item.getQuantidade())),
                    item.getProduct().getLarguraCm(),
                    item.getProduct().getAlturaCm(),
                    item.getProduct().getComprimentoCm()
            );

            volumes.add(volume);
        }

        BigDecimal insuranceValue = order.getItens().stream()
                .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        MeCartRequest requestCart = new MeCartRequest(
                serviceId,
                from,
                address,
                products,
                volumes,
                insuranceValue.compareTo(BigDecimal.ZERO) > 0 ? insuranceValue : null
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

        restClient.post()
                .uri("/api/v2/me/shipment/checkout")
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
