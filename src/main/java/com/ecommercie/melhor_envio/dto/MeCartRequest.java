package com.ecommercie.melhor_envio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeCartRequest(
        Integer service,
        MeAddress from,
        MeAddress to,
        List<MeProduct> products,
        List<MeVolume> volumes,
        MeOptions options
) {

    public record MeAddress(
            String name,
            String phone,
            String email,
            String document,
            String postal_code,
            String address,
            String number,
            String district,
            String city,
            String state_abbr,
            String country_id
    ) {}

    public record MeProduct(
            String name,
            int quantity,
            BigDecimal unitary_value
    ) {}

    public record MeVolume(
            BigDecimal weight,
            BigDecimal width,
            BigDecimal height,
            BigDecimal length
    ) {
        /**
         * Junta todos os itens do pedido em UM volume (os Correios nao aceitam mais de um por envio).
         * Peso: soma de peso x quantidade. Largura e comprimento: o maior entre os itens. Altura: soma
         * de altura x quantidade (as pecas ficam empilhadas).
         */
        public static MeVolume agrupando(java.util.List<EtiquetaRequest.Item> itens) {
            BigDecimal peso = BigDecimal.ZERO;
            BigDecimal altura = BigDecimal.ZERO;
            BigDecimal largura = BigDecimal.ZERO;
            BigDecimal comprimento = BigDecimal.ZERO;
            for (EtiquetaRequest.Item i : itens) {
                BigDecimal qtd = BigDecimal.valueOf(i.quantidade());
                peso = peso.add(i.pesoKg().multiply(qtd));
                altura = altura.add(i.alturaCm().multiply(qtd));
                largura = largura.max(i.larguraCm());
                comprimento = comprimento.max(i.comprimentoCm());
            }
            return new MeVolume(peso, largura, altura, comprimento);
        }
    }

    /** insurance_value (valor segurado) mora AQUI na API do ME; na raiz do corpo ele e ignorado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeOptions(@JsonProperty("insurance_value") BigDecimal insurance_value, MeInvoice invoice, MeDce dce) {
        public record MeInvoice(String key) {}
        public record MeDce(String key) {}
    }
}
