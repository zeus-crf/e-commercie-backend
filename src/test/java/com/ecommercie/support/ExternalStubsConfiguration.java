package com.ecommercie.support;

import com.ecommercie.melhor_envio.ShippingProvider;
import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.mercado_pago.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Troca os provedores externos por stubs. Importe na classe de teste:
 *   @Import({TestcontainersConfiguration.class, ExternalStubsConfiguration.class})
 *
 * Funciona por @Primary porque ninguem injeta MercadoPagoClient/MelhorEnvioClient pela classe
 * concreta — todo o codigo depende das interfaces.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ExternalStubsConfiguration {

    @Bean
    @Primary
    PaymentGateway stubPaymentGateway() {
        return new StubPaymentGateway();
    }

    @Bean
    @Primary
    ShippingProvider stubShippingProvider(ShippimentRepository shippimentRepository) {
        return new StubShippingProvider(shippimentRepository);
    }
}