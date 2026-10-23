package com.ecommercie.support;

import com.ecommercie.pedido.cep.ConsultaCep;
import com.ecommercie.pedido.cep.ResultadoCep;
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
    StubPaymentGateway stubPaymentGateway() {
        return new StubPaymentGateway();
    }

    @Bean
    @Primary
    StubShippingProvider stubShippingProvider() {
        return new StubShippingProvider();
    }

    // todo CEP "existe" e e de SP (a UF dos enderecos dos testes)
    @Bean
    @Primary
    ConsultaCep stubConsultaCep() {
        return cep -> ResultadoCep.encontrado("SP");
    }
}