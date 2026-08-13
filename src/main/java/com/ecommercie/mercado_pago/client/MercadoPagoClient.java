package com.ecommercie.mercado_pago.client;


import com.ecommercie.mercado_pago.PaymentGateway;
import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.pedido.models.Order;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MercadoPagoClient implements PaymentGateway {

    @Value("${mercado-pago-access_token}")
    private String accessToken;

    @Value("${mercado-pago-notification_url}")
    private String notificationUrl;

    @PostConstruct
    public void init(){
        MercadoPagoConfig.setAccessToken(accessToken);
        log.info("Iniciando Mercado Pago");
    }

    @Override
    public PaymentPreference  criarPreferencia(Order order, RequestPreference requestPreference) throws MPException, MPApiException {

        try {
            PreferenceClient client = new PreferenceClient();

            List<PreferenceItemRequest> itens = order.getItens().stream()
                    .map(i -> PreferenceItemRequest.builder()
                            .id(i.getId())
                            .title(i.getNomeProduto())
                            .description(i.getProduct().getDescricao())
                            .quantity(i.getQuantidade())
                            .unitPrice(i.getPrecoUnitario())
                            .build()
                    ).toList();

            PreferencePayerRequest payer = PreferencePayerRequest.builder()
                    .name(order.getUser().getNome())
                    .email(order.getUser().getEmail())
                    .build();

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(requestPreference.backUrls().success())
                    .pending(requestPreference.backUrls().pending())
                    .failure(requestPreference.backUrls().failure())
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(itens)
                    .payer(payer)
                    .backUrls(backUrls)
                    .notificationUrl(notificationUrl)
                    .externalReference(order.getId())
                    .autoReturn("approved")
                    .build();


            Preference preference = client.create(request);

            return new PaymentPreference(preference.getId(), preference.getSandboxInitPoint());


        } catch (MPApiException ex) {
            log.error("Erro ao criar preferência na API do mercado pago: {}", ex.getMessage());
            throw ex;
        }
        catch (MPException ex) {
            log.error("Erro ao criar preferência no Mercado Pago: {}", ex.getMessage());
            throw ex;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
