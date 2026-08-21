package com.ecommercie.outbox.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;


    public void enviarConfirmacaoPedido(String orderId, String destino) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destino);
        message.setSubject("Pagamento Confirmado - pedido " + orderId);
        message.setText("Seu pagamento foi confirmado! Pedido: " + orderId);
        mailSender.send(message);

    }

    public void enviarConfirmacaoEnviado(String orderId, String destino) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destino);
        message.setSubject("Pedido enviado com sucesso! Pedido - " + orderId);
        message.setText("Seu pedido foi enviado com sucesso para " + destino);
        mailSender.send(message);
    }

    public void enviarConfirmacaoCancelado(String orderId, String destino) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destino);
        message.setSubject("Pedido cancelado com sucesso! Pedido - " + orderId);
        message.setText("Seu pedido foi cancelado");
        mailSender.send(message);
    }

    public void enviarConfirmacaoEntregue(String orderId, String destino) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destino);
        message.setSubject("Pedido entregue com sucesso! Pedido - " + orderId);
        message.setText("Seu pedido foi entregue no destino " + destino);
        mailSender.send(message);
    }

    public void enviarReembolsoSolicitado(String orderId, String destino) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destino);
        message.setSubject("Solicitação de devolução recebida - Pedido " + orderId);
        message.setText("Recebemos sua solicitação de devolução para o pedido " + orderId + ". Em breve nossa equipe entrará em contato.");
        mailSender.send(message);
    }

    public void enviarReembolsoConfirmado(String orderId, String destino) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destino);
        message.setSubject("Reembolso confirmado - Pedido " + orderId);
        message.setText("Seu reembolso referente ao pedido " + orderId + " foi processado com sucesso.");
        mailSender.send(message);
    }





}
