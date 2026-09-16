package com.ecommercie.fiscal;

import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FocusNfeFiscalProvider implements FiscalProvider {

    private static final ZoneId ZONA_FISCAL = ZoneId.of("America/Sao_Paulo");
    private static final String UNIDADE_PADRAO = "UN";

    private final FiscalDocumentType tipo;
    private final RestClient restClient;
    private final Emitente emitente;

    /**
     * Dados do emissor e a politica tributaria dele. Vem da configuracao (sao do cliente,
     * nao do codigo) — por isso entram no construtor em vez de ficarem fixos no payload.
     */
    public record Emitente(
            String cnpj,
            String inscricaoEstadual,
            String uf,
            Integer regimeTributario,       // 1 = Simples Nacional, 3 = Regime Normal
            String icmsSituacaoTributaria,  // CSOSN (Simples) ou CST (Normal)
            String pisSituacaoTributaria,
            String cofinsSituacaoTributaria
    ) {}

    public FocusNfeFiscalProvider(FiscalDocumentType tipo, String token, String baseUrl, Emitente emitente) {
        this.tipo = tipo;
        this.emitente = emitente;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // Focus NFe usa HTTP Basic: token como usuario, senha em branco.
                .defaultHeaders(h -> h.setBasicAuth(token, ""))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public FiscalDocument emitir(Order order) {
        String ref = "ecommercie-" + order.getId();

        if (tipo == FiscalDocumentType.DCE){
            return emitirDCE(order, ref);
        } else {
            return emitirNFE(order, ref);
        }
    }

    private FiscalDocument emitirNFE(Order order, String ref) {
        // Idempotencia: a ref e derivada do pedido, entao uma reentrega do outbox cai aqui de novo.
        // Consultar antes evita repetir o POST de uma nota que ja foi autorizada.
        FiscalDocument jaEmitida = consultarNfe(ref);
        if (jaEmitida != null) {
            log.info("NF-e ja autorizada para pedido {} (ref={}) — reaproveitando chave", order.getId(), ref);
            return jaEmitida;
        }

        Map<String, Object> payload = montarPayloadNfe(order);

        Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.post()
                    .uri("/v2/nfe?ref={ref}", ref)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            response = resp;
        } catch (Exception ex) {
            log.error("Erro ao emitir NF-e para pedido {}: {}", order.getId(), ex.getMessage());
            throw new RuntimeException("Falha na emissao de NF-e: " + ex.getMessage(), ex);
        }

        return interpretarResposta(response, order.getId(), ref);
    }

    /**
     * Monta o corpo da NF-e conforme a referencia de campos da Focus NFe.
     * Os campos do destinatario sao achatados no topo (sufixo _destinatario) e o array de itens
     * chama-se "items" (em ingles) na NF-e — diferente do "itens" da DC-e.
     */
    private Map<String, Object> montarPayloadNfe(Order order) {
        var user = order.getUser();
        var address = order.getAddress();

        BigDecimal valorFrete = order.getValorFrete() != null ? order.getValorFrete() : BigDecimal.ZERO;
        BigDecimal valorTotal = order.getValorItens().add(valorFrete);

        String documento = somenteDigitos(user.getCpfCnpj());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("natureza_operacao", "Venda de mercadoria");
        payload.put("data_emissao", ZonedDateTime.now(ZONA_FISCAL).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("tipo_documento", 1);                 // 1 = saida
        payload.put("finalidade_emissao", 1);             // 1 = normal
        payload.put("local_destino", localDestino(address.getUf()));
        payload.put("consumidor_final", 1);
        payload.put("presenca_comprador", 2);             // 2 = operacao pela internet
        payload.put("modalidade_frete", 0);               // 0 = por conta do emitente (CIF)
        payload.put("valor_frete", valorFrete);

        payload.put("cnpj_emitente", somenteDigitos(emitente.cnpj()));
        payload.put("inscricao_estadual_emitente", emitente.inscricaoEstadual());
        payload.put("regime_tributario_emitente", emitente.regimeTributario());

        payload.put("nome_destinatario", user.getNome());
        if (documento.length() == 11) {
            payload.put("cpf_destinatario", documento);
        } else {
            payload.put("cnpj_destinatario", documento);
        }
        payload.put("indicador_inscricao_estadual_destinatario", 9); // 9 = nao contribuinte
        payload.put("logradouro_destinatario", address.getLogradouro());
        payload.put("numero_destinatario", address.getNumero());
        payload.put("bairro_destinatario", address.getBairro());
        payload.put("municipio_destinatario", address.getCidade());
        payload.put("uf_destinatario", address.getUf());
        payload.put("cep_destinatario", somenteDigitos(address.getCep()));

        payload.put("valor_total", valorTotal);
        payload.put("fcp_valor_total", BigDecimal.ZERO);
        payload.put("items", montarItens(order));

        return payload;
    }

    private List<Map<String, Object>> montarItens(Order order) {
        List<Map<String, Object>> items = new ArrayList<>();
        int numero = 1;

        for (OrderItem item : order.getItens()) {
            BigDecimal valorBruto = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));

            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("numero_item", numero);
            linha.put("codigo_produto", item.getProduct() != null ? item.getProduct().getId() : "SKU-" + numero);
            linha.put("descricao", item.getNomeProduto());
            linha.put("codigo_ncm", item.getNcm());
            linha.put("cfop", item.getCfop());
            linha.put("unidade_comercial", UNIDADE_PADRAO);
            linha.put("quantidade_comercial", item.getQuantidade());
            linha.put("valor_unitario_comercial", item.getPrecoUnitario());
            linha.put("valor_bruto", valorBruto);
            linha.put("icms_origem", item.getOrigem());
            linha.put("icms_situacao_tributaria", emitente.icmsSituacaoTributaria());
            linha.put("pis_situacao_tributaria", emitente.pisSituacaoTributaria());
            linha.put("cofins_situacao_tributaria", emitente.cofinsSituacaoTributaria());

            items.add(linha);
            numero++;
        }

        return items;
    }

    /**
     * A Focus responde 201 (autorizado, ja com chave) ou 202 (processando_autorizacao, sem chave).
     * No caso 202 lancamos excecao: o outbox reprocessa o evento e a consulta no inicio de
     * emitirNFE devolve a chave assim que a SEFAZ autorizar.
     */
    private FiscalDocument interpretarResposta(Map<String, Object> response, String orderId, String ref) {
        if (response == null) {
            throw new RuntimeException("Falha na emissao de NF-e: resposta vazia da Focus NFe (ref=" + ref + ")");
        }

        String status = (String) response.get("status");
        String chave = (String) response.get("chave_nfe");

        if ("autorizado".equals(status) && chave != null && !chave.isBlank()) {
            log.info("NF-e emitida com sucesso para pedido {}: chave={}", orderId, chave);
            return new FiscalDocument(FiscalDocumentType.NFE, chave);
        }

        if ("processando_autorizacao".equals(status)) {
            log.info("NF-e do pedido {} em processamento na SEFAZ (ref={}) — sera reconsultada", orderId, ref);
            throw new RuntimeException("NF-e ainda em processamento na SEFAZ (ref=" + ref + ")");
        }

        String mensagem = (String) response.get("mensagem_sefaz");
        log.error("NF-e rejeitada para pedido {} (ref={}): status={} mensagem={}", orderId, ref, status, mensagem);
        throw new RuntimeException("Falha na emissao de NF-e: status=" + status + " mensagem=" + mensagem);
    }

    /** Devolve o documento se a ref ja estiver autorizada; null se nao existir ou ainda nao tiver chave. */
    private FiscalDocument consultarNfe(String ref) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/v2/nfe/{ref}", ref)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return null;
            }

            String chave = (String) response.get("chave_nfe");
            if ("autorizado".equals(response.get("status")) && chave != null && !chave.isBlank()) {
                return new FiscalDocument(FiscalDocumentType.NFE, chave);
            }
            return null;
        } catch (Exception ex) {
            // 404 = ref ainda nao enviada. Outro erro aqui nao deve impedir a tentativa de emissao.
            log.debug("Consulta previa da NF-e (ref={}) nao retornou nota: {}", ref, ex.getMessage());
            return null;
        }
    }

    /** 1 = operacao interna, 2 = interestadual. Comparado contra a UF do emitente. */
    private int localDestino(String ufDestinatario) {
        return emitente.uf() != null && emitente.uf().equalsIgnoreCase(ufDestinatario) ? 1 : 2;
    }

    private String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public FiscalDocument emitirDCE(Order order, String ref) {
        var itens = order.getItens().stream().map(i -> Map.of(
                "descricao", i.getNomeProduto(),
                "quantdade", i.getQuantidade(),
                "valor" , i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade()))
        )).toList();

        var payload = Map.of(
                "remetente_nome", order.getUser().getNome(),
                "remetente_cpf_cnpj", order.getUser().getCpfCnpj(),
                "destinatario_nome", order.getUser().getNome(),
                "destinatario_cep", order.getAddress().getCep(),
                "itens", itens
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/v2/dce?ref=", ref)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            String chave = (String) response.get("chave_acesso");
            log.info("DC-e emitida com sucesso para pedido {}: chave={}", order.getId(), chave);

            return new FiscalDocument(tipo, chave);

        } catch (Exception ex) {
            log.error("Erro ao emitir DC-e para pedido {}: {}", order.getId(), ex.getMessage());
            throw new RuntimeException("Falha na emissao de DC-e: " + ex.getMessage(), ex);
        }
    }
}
