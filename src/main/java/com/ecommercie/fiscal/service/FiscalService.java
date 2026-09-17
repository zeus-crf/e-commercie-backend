package com.ecommercie.fiscal.service;

import com.ecommercie.fiscal.FiscalProvider;
import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.fiscal.model.Invoice;
import com.ecommercie.fiscal.repository.InvoiceRepository;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.service.OutboxService;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.repository.OrderRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalService {

    private final FiscalProvider fiscalProvider;
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;


    @Transactional
    public void emitirParaPedido(String orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        Optional<Invoice> existing = invoiceRepository.findByOrderId(orderId);

        if (existing.isPresent() && "AUTORIZADO".equals(existing.get().getStatus())){
            log.info("Invoice já autorizada para pedido {} - ignorando", orderId);
            return;
        }

        FiscalDocument doc = fiscalProvider.emitir(order);
        Invoice invoice = Invoice.builder()
                .order(order)
                .tipo(doc.tipo())
                .chave(doc.chave())
                .status(doc.tipo() == FiscalDocumentType.NOOP ? "NOOP" : "AUTORIZADO")
                .build();


        invoiceRepository.save(invoice);
        log.info("Invoice criada para pedido {} - tipo={}  status={}", orderId, doc.tipo(), invoice.getStatus());

    }

    public Optional<Invoice> buscarPorPedido(String orderId){
        return invoiceRepository.findByOrderId(orderId);
    }

    public void agendarEmissao(String orderId, String email){
        outboxService.registrar(OutboxTypes.FISCAL_EMISSAO, new FiscalPayload(orderId, email));
    }

    public record FiscalPayload(String orderId, String email) {}
}
