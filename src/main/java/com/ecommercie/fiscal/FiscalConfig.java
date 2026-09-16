package com.ecommercie.fiscal;

import com.ecommercie.fiscal.enums.FiscalDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FiscalConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "features.fiscal.enabled", havingValue = "true")
    public FiscalProvider focusNfeFiscalProvider(
            @Value("${features.fiscal.tipo:DCE}") String tipo,
            @Value("${focus.token:}") String token,
            @Value("${focusnfe.base-url:https://homologacao.focusnfe.com.br}") String baseUrl
    ){
        return new FocusNfeProvider(FiscalDocumentType.valueOf(tipo.toUpperCase()), baseUrl);
    }

}
