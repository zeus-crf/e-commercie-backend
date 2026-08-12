package com.ecommercie.mercado_pago.client;


import com.ecommercie.mercado_pago.dtos.RequestPreference;
import com.ecommercie.mercado_pago.dtos.ResponsePreference;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class MercadoPagoClient {

    @Value("${mercado-pago-access_token}")
    private String accessToken;

    @Value("${mercado-pago-notification_url}")
    private String notificationUrl;

    public void init(){
        MercadoPagoConfig.setAccessToken(accessToken);
        log.info("Iniciando Mercado Pago");
    }

    public ResponsePreference createPreference(RequestPreference requestPreference, String orderId) throws MPException, MPApiException {

        try {
            PreferenceClient client = new PreferenceClient();

            List<PreferenceItemRequest> itens = requestPreference.itemsDto().stream()
                    .map(i -> PreferenceItemRequest.builder()
                            .id(i.id())
                            .title(i.title())
                            .description(i.description())
                            .quantity(i.quantity())
                            .unitPrice(i.unitPrice())
                            .build()
                    ).toList();

            PreferencePayerRequest payer = PreferencePayerRequest.builder()
                    .name(requestPreference.payer().nome())
                    .email(requestPreference.payer().email())
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
                    .externalReference(orderId)            //ID do pedido
                    .autoReturn("approved")
                    .build();

            Preference preference = client.create(request);

            return new ResponsePreference(
                    preference.getId(),
                    preference.getNotificationUrl()
            );

        } catch (MPApiException ex) {
            log.error("Erro ao criar preferência na API do mercado pago: {}", ex.getMessage());
        }
        catch (MPException ex) {
            log.error("Erro ao criar preferência no Mercado Pago: {}", ex.getMessage());
            throw ex;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }


        return null;

    }


}
