package com.ecommercie.fiscal.dto;

import com.ecommercie.fiscal.enums.InvoiceStatus;
import com.ecommercie.fiscal.model.Invoice;

public record InvoiceResponse(String id, String tipo, String chave, InvoiceStatus status) {

    public static InvoiceResponse from(Invoice inv){
        return new InvoiceResponse(inv.getId(), inv.getTipo().name(), inv.getChave(), inv.getStatus());
    }
}
