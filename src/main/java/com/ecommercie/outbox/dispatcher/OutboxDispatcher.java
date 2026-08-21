package com.ecommercie.outbox.dispatcher;

import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.models.OutboxEvent;
import com.ecommercie.outbox.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;


    public void dispatch(OutboxEvent event) throws Exception {
        switch (event.getType()){

            case OutboxTypes.EMAIL_CONFIRMACAO_PEDIDO -> {
                var p = objectMapper.readValue(event.getPayload(), EmailPayload.class);
                emailService.enviarConfirmacaoPedido(p.orderId(), p.email());
            }

            case OutboxTypes.EMAIL_PEDIDO_ENVIADO -> {
                var p = objectMapper.readValue(event.getPayload(), EmailPayload.class);
                emailService.enviarConfirmacaoEnviado(p.orderId(), p.email());
            }

            case OutboxTypes.EMAIL_PEDIDO_CANCELADO -> {
                var p = objectMapper.readValue(event.getPayload(), EmailPayload.class);
                emailService.enviarConfirmacaoCancelado(p.orderId(), p.email());
            }

            case OutboxTypes.EMAIL_PEDIDO_ENTREGUE -> {
                var p = objectMapper.readValue(event.getPayload(), EmailPayload.class);
                emailService.enviarConfirmacaoEntregue(p.orderId(), p.email());
            }

            case OutboxTypes.EMAIL_REEMBOLSO_SOLICITADO -> {
                var p = objectMapper.readValue(event.getPayload(), EmailPayload.class);
                emailService.enviarReembolsoSolicitado(p.orderId(), p.email());
            }

            case OutboxTypes.EMAIL_REEMBOLSO_CONFIRMADO -> {
                var p = objectMapper.readValue(event.getPayload(), EmailPayload.class);
                emailService.enviarReembolsoConfirmado(p.orderId(), p.email());
            }

            default -> throw new IllegalStateException("Tipo de outbox desconhecido: " + event.getType());
        }
    }

    public record EmailPayload(String orderId, String email){}
}
