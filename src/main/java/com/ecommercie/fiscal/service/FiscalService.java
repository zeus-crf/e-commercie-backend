package com.ecommercie.fiscal.service;

import com.ecommercie.fiscal.FiscalProvider;
import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.fiscal.enums.InvoiceStatus;
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
import org.springframework.transaction.annotation.Propagation;
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


    /**
     * REQUIRES_NEW: o relay do outbox chama este método de dentro da transação dele, que despacha um
     * lote inteiro. Numa transação compartilhada, uma falha aqui marcaria o lote como rollback-only e
     * derrubaria junto os eventos que já tinham dado certo — e o markFailed do relay nunca seria
     * aplicado, fazendo o mesmo lote voltar a cada ciclo. Em transação própria, a falha fica contida
     * neste evento e o relay consegue reagendá-lo com backoff.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emitirParaPedido(String orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Pedido não encontrado"));

        // Um documento fiscal por pedido (UNIQUE em pedido_id). A linha existente é REAPROVEITADA em
        // vez de dar lugar a uma nova: o outbox é at-least-once, e uma invoice NOOP (toggle desligado)
        // precisa poder virar uma emissão real depois, sem violar a constraint.
        Invoice invoice = invoiceRepository.findByOrderId(orderId).orElse(null);

        if (invoice != null && invoice.getStatus() == InvoiceStatus.AUTORIZADO){
            log.info("Invoice já autorizada para pedido {} - ignorando", orderId);
            return;
        }

        FiscalDocument doc = fiscalProvider.emitir(order);

        if (invoice == null) {
            invoice = Invoice.builder().order(order).build();
        }

        invoice.setTipo(doc.tipo());
        invoice.setChave(doc.chave());
        invoice.setStatus(doc.tipo() == FiscalDocumentType.NOOP ? InvoiceStatus.NOOP : InvoiceStatus.AUTORIZADO);

        // saveAndFlush: força o INSERT/UPDATE agora. Sem isso o flush só aconteceria no commit, e uma
        // violação de constraint escaparia do try/catch de quem chamou.
        invoiceRepository.saveAndFlush(invoice);
        log.info("Invoice gravada para pedido {} - tipo={}  status={}", orderId, doc.tipo(), invoice.getStatus());

    }

    public Optional<Invoice> buscarPorPedido(String orderId){
        return invoiceRepository.findByOrderId(orderId);
    }

    public void agendarEmissao(String orderId){
        outboxService.registrar(OutboxTypes.FISCAL_EMISSAO, new FiscalPayload(orderId));
    }

    public record FiscalPayload(String orderId) {}
}
