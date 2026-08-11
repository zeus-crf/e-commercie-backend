package com.ecommercie.pedido.dtos;

import com.ecommercie.pedido.models.Address;

public record AddressResponse(
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getLogradouro(),
                address.getNumero(),
                address.getBairro(),
                address.getCidade(),
                address.getUf(),
                address.getCep()
        );
    }
}
