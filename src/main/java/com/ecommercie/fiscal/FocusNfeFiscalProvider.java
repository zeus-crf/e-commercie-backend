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
            Integer regimeTributario,
            String icmsSituacaoTributaria,
            String pisSituacaoTributaria,
            String cofinsSituacaoTributaria,
            String tipoEmitente,          // DC-e: "1" a "4"
            String modalidadeTransporte   // DC-e: "0", "1" ou "2"
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
     * Payload da DC-e conforme https://doc.focusnfe.com.br/reference/emitir_dce.md
     * Obrigatórios: cnpj_emitente, tipo_emitente, nome_destinatario, itens, modalidade_transporte.
     * A loja é o remetente (emitente); o cliente é o destinatário.
     */
    private Map<String, Object> montarPayloadDce(Order order) {
        var user = order.getUser();
        var address = order.getAddress();
        String documento = somenteDigitos(user.getCpfCnpj());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cnpj_emitente", somenteDigitos(emitente.cnpj()));
        payload.put("tipo_emitente", emitente.tipoEmitente());
        payload.put("modalidade_transporte", emitente.modalidadeTransporte());

        payload.put("nome_destinatario", user.getNome());
        if (documento.length() == 11) {
            payload.put("cpf_destinatario", documento);
        } else {
            payload.put("cnpj_destinatario", documento);
        }
        payload.put("logradouro_destinatario", address.getLogradouro());
        payload.put("numero_destinatario", address.getNumero());
        payload.put("bairro_destinatario", address.getBairro());
        payload.put("municipio_destinatario", address.getCidade());
        payload.put("uf_destinatario", address.getUf());
        payload.put("cep_destinatario", somenteDigitos(address.getCep()));
        payload.put("email_destinatario", user.getEmail());

        payload.put("itens", montarItensDce(order));
        payload.put("informacoes_complementares", "Pedido " + order.getId());

        return payload;
    }

    private List<Map<String, Object>> montarItensDce(Order order) {
        List<Map<String, Object>> itens = new ArrayList<>();
        int numero = 1;

        for (OrderItem item : order.getItens()) {
            BigDecimal valorProduto = item.getPrecoUnitario()
                    .multiply(BigDecimal.valueOf(item.getQuantidade()));

            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("numero", String.valueOf(numero));
            linha.put("descricao_produto", item.getNomeProduto());
            linha.put("codigo_ncm", item.getNcm());
            linha.put("quantidade", item.getQuantidade());
            linha.put("valor_unitario", item.getPrecoUnitario());
            linha.put("valor_produto", valorProduto);

            itens.add(linha);
            numero++;
        }

        return itens;
    }


    private Map<String, Object> montarPayloadNfe(Order order) {
        var user = order.getUser();
        var address = order.getAddress();
        String documento = somenteDigitos(user.getCpfCnpj());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cnpj_emitente", somenteDigitos(emitente.cnpj()));
        payload.put("tipo_emitente", emitente.tipoEmitente());
        payload.put("modalidade_transporte", emitente.modalidadeTransporte());

        payload.put("nome_destinatario", user.getNome());
        if (documento.length() == 11) {
            payload.put("cpf_destinatario", documento);
        } else {
            payload.put("cnpj_destinatario", documento);
        }
        payload.put("logradouro_destinatario", address.getLogradouro());
        payload.put("numero_destinatario", address.getNumero());
        payload.put("bairro_destinatario", address.getBairro());
        payload.put("municipio_destinatario", address.getCidade());
        payload.put("uf_destinatario", address.getUf());
        payload.put("cep_destinatario", somenteDigitos(address.getCep()));
        payload.put("email_destinatario", user.getEmail());

        payload.put("itens", montarItensDce(order));
        payload.put("informacoes_complementares", "Pedido " + order.getId());

        return payload;
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
