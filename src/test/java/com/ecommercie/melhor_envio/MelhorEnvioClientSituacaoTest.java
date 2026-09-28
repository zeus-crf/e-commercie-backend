package com.ecommercie.melhor_envio;

import com.ecommercie.melhor_envio.client.MelhorEnvioClient;
import com.ecommercie.melhor_envio.dto.MeOrderResponse;
import com.ecommercie.melhor_envio.enums.SituacaoEtiqueta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MelhorEnvioClientSituacaoTest {

    private static final String DATA = "2026-09-25 10:00:00";


    @DisplayName("Verifica o status do envios")
    @Test
    void decidePelasDatasDoMelhorEnvio() {
        // No Carrinho: pending, sem data de pagamento
        assertThat(situacao("pending", null, null,null)).isEqualTo(SituacaoEtiqueta.PENDENTE_NO_CARRINHO);

        // Paga, nao importa em que etapa esteja depois
        assertThat(situacao("released", DATA, null, null)).isEqualTo(SituacaoEtiqueta.PAGA);
        assertThat(situacao("posted", DATA, null, null)).isEqualTo(SituacaoEtiqueta.PAGA);
        assertThat(situacao("suspended", DATA, null, null)).isEqualTo(SituacaoEtiqueta.PAGA);


        // Cancelado ou expirado vence, mesmo que tenha sido paga antes(estornada)
        assertThat(situacao("canceled", DATA, DATA, null)).isEqualTo(SituacaoEtiqueta.CANCELADA);
        assertThat(situacao("pending", null, null, DATA)).isEqualTo(SituacaoEtiqueta.CANCELADA);

        // Sem data de pagamento e fora do carrinho: nao adivinhar
        assertThat(situacao("algo-novo", null, null, null)).isEqualTo(SituacaoEtiqueta.INDEFINIDA);
        assertThat(MelhorEnvioClient.situacaoDo(null)).isEqualTo(SituacaoEtiqueta.INDEFINIDA);

    }


    private static SituacaoEtiqueta situacao(String status, String pagaEm, String canceladoEm, String expiradaEm) {
        return MelhorEnvioClient.situacaoDo(new MeOrderResponse("id", status, pagaEm, canceladoEm, expiradaEm));
    }
}
