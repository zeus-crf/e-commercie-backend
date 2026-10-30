package com.ecommercie.melhor_envio;

import com.ecommercie.melhor_envio.dto.CalculePayload;
import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.MeCartRequest;
import com.ecommercie.melhor_envio.dto.ShippingItem;
import com.ecommercie.melhor_envio.dto.ShippingQuoteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Valor segurado: a etiqueta manda em options.insurance_value e a cotacao considera o mesmo valor. */
class MeSeguroTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static EtiquetaRequest.Item item(String preco, int qtd) {
        return new EtiquetaRequest.Item("Camiseta", qtd, new BigDecimal(preco),
                new BigDecimal("0.3"), new BigDecimal("20"), new BigDecimal("2"), new BigDecimal("30"));
    }

    private static EtiquetaRequest etiqueta(EtiquetaRequest.DocumentoFiscal doc, EtiquetaRequest.Item... itens) {
        return new EtiquetaRequest("pedido-1", 1, null, List.of(itens), doc);
    }

    private static MeCartRequest carrinho(BigDecimal seguro, MeCartRequest.MeOptions.MeInvoice invoice) {
        var options = seguro == null && invoice == null ? null : new MeCartRequest.MeOptions(seguro, invoice, null);
        return new MeCartRequest(1, null, null, List.of(), List.of(), options);
    }

    // ----------------- etiqueta -----------------

    @Test
    void etiqueta_valorSegurado_somaPrecoVezesQuantidadeDeTodosOsItens() {
        var req = etiqueta(null, item("59.90", 2), item("69.90", 1));

        assertThat(req.valorSegurado()).isEqualByComparingTo("189.70");   // 2 x 59,90 + 69,90
    }

    @Test
    void etiqueta_mandaOValorSeguradoDentroDeOptions_enaoNaRaiz() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(carrinho(new BigDecimal("189.70"), null)));

        assertThat(json.path("options").path("insurance_value").decimalValue()).isEqualByComparingTo("189.70");
        assertThat(json.has("insurance_value")).isFalse();   // na raiz o ME ignora
    }

    @Test
    void etiqueta_comNota_mandaSeguroEChaveJuntosEmOptions() throws Exception {
        var invoice = new MeCartRequest.MeOptions.MeInvoice("NFe123");
        JsonNode options = mapper.readTree(mapper.writeValueAsString(carrinho(new BigDecimal("50"), invoice))).path("options");

        assertThat(options.path("insurance_value").decimalValue()).isEqualByComparingTo("50");
        assertThat(options.path("invoice").path("key").asText()).isEqualTo("NFe123");
        assertThat(options.has("dce")).isFalse();
    }

    @Test
    void etiqueta_semSeguroENemNota_naoMandaOptions() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(carrinho(null, null)));

        assertThat(json.has("options")).isFalse();
    }

    // ----------------- cotacao -----------------

    private static ShippingItem cotacaoItem(String preco, int qtd) {
        return new ShippingItem(new BigDecimal("0.3"), new BigDecimal("2"), new BigDecimal("20"),
                new BigDecimal("30"), qtd, preco == null ? null : new BigDecimal(preco));
    }

    @Test
    void cotacao_valorSegurado_somaOsItensQueInformaramPreco() {
        var req = new ShippingQuoteRequest("01310100", List.of(cotacaoItem("59.90", 2), cotacaoItem("69.90", 1)));

        assertThat(req.valorSegurado()).isEqualByComparingTo("189.70");
    }

    @Test
    void cotacao_semPrecoNenhum_naoConsideraSeguro() {
        var req = new ShippingQuoteRequest("01310100", List.of(cotacaoItem(null, 1)));

        assertThat(req.valorSegurado()).isNull();
        assertThat(new ShippingQuoteRequest("01310100", List.of()).valorSegurado()).isNull();
    }

    @Test
    void cotacao_payloadDoMe_levaInsuranceValueEmOptions() throws Exception {
        var payload = new CalculePayload(new CalculePayload.PostalCode("25240120"),
                new CalculePayload.PostalCode("01310100"), List.of(), new CalculePayload.Options(new BigDecimal("189.70")));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(payload));

        assertThat(json.path("options").path("insurance_value").decimalValue()).isEqualByComparingTo("189.70");
    }

    @Test
    void cotacao_payloadDoMe_semSeguro_naoMandaOptions() throws Exception {
        var payload = new CalculePayload(new CalculePayload.PostalCode("25240120"),
                new CalculePayload.PostalCode("01310100"), List.of(), null);

        assertThat(mapper.readTree(mapper.writeValueAsString(payload)).has("options")).isFalse();
    }
}
