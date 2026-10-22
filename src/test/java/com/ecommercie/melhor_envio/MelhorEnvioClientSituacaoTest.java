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
        // No carrinho: pending, sem data de pagamento
        assertThat(situacao("pending", null, null, null, null)).isEqualTo(SituacaoEtiqueta.PENDENTE_NO_CARRINHO);

        // Paga e ainda nao gerada
        assertThat(situacao("released", DATA, null, null, null)).isEqualTo(SituacaoEtiqueta.PAGA);

        // Gerada vence paga, nao importa a etapa seguinte
        assertThat(situacao("generated", DATA, DATA, null, null)).isEqualTo(SituacaoEtiqueta.GERADA);
        assertThat(situacao("posted", DATA, DATA, null, null)).isEqualTo(SituacaoEtiqueta.GERADA);

        // Cancelado ou expirado vence tudo, mesmo que tenha sido paga/gerada antes (estornada)
        assertThat(situacao("canceled", DATA, DATA, DATA, null)).isEqualTo(SituacaoEtiqueta.CANCELADA);
        assertThat(situacao("pending", null, null, null, DATA)).isEqualTo(SituacaoEtiqueta.CANCELADA);

        // Sem data de pagamento e fora do carrinho: nao adivinhar
        assertThat(situacao("algo-novo", null, null, null, null)).isEqualTo(SituacaoEtiqueta.INDEFINIDA);
        assertThat(MelhorEnvioClient.situacaoDo(null)).isEqualTo(SituacaoEtiqueta.INDEFINIDA);
    }


    private static SituacaoEtiqueta situacao(String status, String pagaEm, String geradaEm, String canceladoEm, String expiradaEm) {
        return MelhorEnvioClient.situacaoDo(new MeOrderResponse("id", status, pagaEm, geradaEm, canceladoEm, expiradaEm));
    }
}
