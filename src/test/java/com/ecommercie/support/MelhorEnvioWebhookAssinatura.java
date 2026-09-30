package com.ecommercie.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Assina o corpo de um webhook como o Melhor Envio faz: Base64(HMAC-SHA256(corpo, secret)). */
public final class MelhorEnvioWebhookAssinatura {

    /** mesmo valor de melhorenvio.webhook-secret no src/test/resources/application-test.yml */
    public static final String SEGREDO = "segredo-webhook-de-teste";


    private MelhorEnvioWebhookAssinatura(){}

    public static String assinar(String corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
