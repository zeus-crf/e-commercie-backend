package com.ecommercie.melhor_envio.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Valida o cabecalho X-ME-Signature do webhook do Melhor Envio:
 * Base64(HMAC-SHA256(corpo da requisicao, secret do aplicativo)).
 * Doc: https://docs.melhorenvio.com.br/docs/webhooks
 */
@Component
public class AssinaturaWebhookMelhorEnvio {

    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] segredo;

    public AssinaturaWebhookMelhorEnvio(@Value("${melhorenvio.webhook-secret}") String segredoConfigurado) {
        // UTF-8 explicito: os mesmos bytes que o Melhor Envio usa como chave do HMAC
        this.segredo = segredoConfigurado.getBytes(StandardCharsets.UTF_8);
    }

    // corpo → assinatura que calculamos | assinatura → a que veio no cabeçalho
    public boolean valida(byte[] corpo, String assinatura) {
        if (assinatura == null || assinatura.isBlank() || segredo.length == 0) {
            return false;
        }
        byte[] recebida;

        try {
            recebida = Base64.getDecoder().decode(assinatura.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        // comparacao em tempo constante: nao vaza quantos bytes bateram
        return MessageDigest.isEqual(hmac(corpo), recebida);
    }

    private byte[] hmac(byte[] corpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo, ALGORITMO));
            return mac.doFinal(corpo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponivel", e);
        }
    }
}
