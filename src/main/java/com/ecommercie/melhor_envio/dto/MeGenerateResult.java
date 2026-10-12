package com.ecommercie.melhor_envio.dto;

/** Resultado por etiqueta do POST /shipment/generate: {"<id>": {"status": true, "message": "..."}}. */
public record MeGenerateResult(boolean status, String message) {
}
