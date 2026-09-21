package com.ecommercie.fiscal;

import com.ecommercie.fiscal.enums.FiscalDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FiscalConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "features.fiscal.enabled", havingValue = "true")
    public FiscalProvider focusNfeFiscalProvider(
            @Value("${features.fiscal.tipo:DCE}") String tipo,
            @Value("${focusnfe.token:}") String token,
            @Value("${focusnfe.base-url:https://homologacao.focusnfe.com.br}") String baseUrl,
            @Value("${focusnfe.emitente.cnpj:}") String cnpj,
            @Value("${focusnfe.emitente.inscricao-estadual:}") String inscricaoEstadual,
            @Value("${focusnfe.emitente.uf:}") String uf,
            @Value("${focusnfe.emitente.regime-tributario:1}") Integer regimeTributario,
            @Value("${focusnfe.emitente.icms-cst:102}") String icmsCst,
            @Value("${focusnfe.emitente.pis-cst:07}") String pisCst,
            @Value("${focusnfe.emitente.cofins-cst:07}") String cofinsCst,
            @Value("${focusnfe.emitente.tipo-emitente:2}") String tipoEmitente,
            @Value("${focusnfe.emitente.modalidade-transporte:0}") String modalidadeTransporte
    ){
        var emitente = new FocusNfeFiscalProvider.Emitente(
                cnpj, inscricaoEstadual, uf, regimeTributario, icmsCst, pisCst, cofinsCst,
                tipoEmitente, modalidadeTransporte
        );

        return new FocusNfeFiscalProvider(
                FiscalDocumentType.valueOf(tipo.toUpperCase()), token, baseUrl, emitente
        );
    }

}
