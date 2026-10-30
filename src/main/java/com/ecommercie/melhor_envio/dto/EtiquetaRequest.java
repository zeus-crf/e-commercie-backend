package com.ecommercie.melhor_envio.dto;

import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.fiscal.model.Invoice;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.shared.documento.CpfCnpj;

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

    /** Valor declarado ao ME: soma de preco x quantidade de todos os itens do pedido. */
    public BigDecimal valorSegurado() {
        return itens.stream()
                .map(i -> i.precoUnitario().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Destinatario(String nome, String email, String documento, String cep, String logradouro,
                               String numero, String bairro, String cidade, String uf) {}

    public record Item(String nome, int quantidade, BigDecimal precoUnitario,
                       BigDecimal pesoKg, BigDecimal larguraCm, BigDecimal alturaCm, BigDecimal comprimentoCm) {}

    public record DocumentoFiscal(FiscalDocumentType tipo, String chave) {}

    public static EtiquetaRequest from(Order order, int serviceId, Optional<Invoice> invoice) {
        var user = order.getUser();
        var end = order.getAddress();
        var destinatario = new Destinatario(user.getNome(), user.getEmail(), CpfCnpj.somenteDigitos(user.getCpfCnpj()), end.getCep(),
                end.getLogradouro(), end.getNumero(), end.getBairro(), end.getCidade(), end.getUf());

        var itens = order.getItens().stream()
                .map(i -> {
                    var v = i.getVariant();
                    var p = i.getProduct();
                    return new Item(i.nomeCompleto(), i.getQuantidade(), i.getPrecoUnitario(),
                            v != null ? v.pesoEfetivo() : p.getPesoKg(),
                            v != null ? v.larguraEfetiva() : p.getLarguraCm(),
                            v != null ? v.alturaEfetiva() : p.getAlturaCm(),
                            v != null ? v.comprimentoEfetivo() : p.getComprimentoCm());
                })
                .toList();

        var documento = invoice
                .filter(inv -> inv.getChave() != null && !inv.getChave().isBlank())
                .map(inv -> new DocumentoFiscal(inv.getTipo(), inv.getChave()))
                .orElse(null);

        return new EtiquetaRequest(order.getId(), serviceId, destinatario, itens, documento);
    }
}
