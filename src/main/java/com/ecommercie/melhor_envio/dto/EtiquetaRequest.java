package com.ecommercie.melhor_envio.dto;

import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.fiscal.model.Invoice;
import com.ecommercie.pedido.models.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Snapshot do que o Melhor Envio precisa para gerar a etiqueta. Montado DENTRO da transacao
 * (le as associacoes lazy do pedido) e usado FORA dela, na chamada HTTP.
 */
public record EtiquetaRequest(
        String orderId,
        int serviceId,
        Destinatario destinatario,
        List<Item> itens,
        DocumentoFiscal documentoFiscal   // null quando nao ha NF-e/DC-e com chave
) {

    public record Destinatario(String nome, String email, String documento, String cep, String logradouro,
                               String numero, String bairro, String cidade, String uf) {}

    public record Item(String nome, int quantidade, BigDecimal precoUnitario,
                       BigDecimal pesoKg, BigDecimal larguraCm, BigDecimal alturaCm, BigDecimal comprimentoCm) {}

    public record DocumentoFiscal(FiscalDocumentType tipo, String chave) {}

    public static EtiquetaRequest from(Order order, int serviceId, Optional<Invoice> invoice) {
        var user = order.getUser();
        var end = order.getAddress();
        var destinatario = new Destinatario(user.getNome(), user.getEmail(), user.getCpfCnpj(), end.getCep(),
                end.getLogradouro(), end.getNumero(), end.getBairro(), end.getCidade(), end.getUf());

        var itens = order.getItens().stream()
                .map(i -> new Item(i.getNomeProduto(), i.getQuantidade(), i.getPrecoUnitario(),
                        i.getProduct().getPesoKg(), i.getProduct().getLarguraCm(),
                        i.getProduct().getAlturaCm(), i.getProduct().getComprimentoCm()))
                .toList();

        var documento = invoice
                .filter(inv -> inv.getChave() != null && !inv.getChave().isBlank())
                .map(inv -> new DocumentoFiscal(inv.getTipo(), inv.getChave()))
                .orElse(null);

        return new EtiquetaRequest(order.getId(), serviceId, destinatario, itens, documento);
    }
}
