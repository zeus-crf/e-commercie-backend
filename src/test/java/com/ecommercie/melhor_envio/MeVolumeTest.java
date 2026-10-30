package com.ecommercie.melhor_envio;

import com.ecommercie.melhor_envio.dto.EtiquetaRequest;
import com.ecommercie.melhor_envio.dto.MeCartRequest.MeVolume;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeVolumeTest {

    private static EtiquetaRequest.Item item(String peso, String largura, String altura, String comprimento, int qtd) {
        return new EtiquetaRequest.Item("Camiseta", qtd, new BigDecimal("50.00"),
                new BigDecimal(peso), new BigDecimal(largura), new BigDecimal(altura), new BigDecimal(comprimento));
    }

    @Test
    void umaLinhaDeUmaUnidade_viraOVolumeDaPropriaUnidade() {
        MeVolume v = MeVolume.agrupando(List.of(item("0.3", "20", "2", "30", 1)));

        assertThat(v.weight()).isEqualByComparingTo("0.3");
        assertThat(v.width()).isEqualByComparingTo("20");
        assertThat(v.height()).isEqualByComparingTo("2");
        assertThat(v.length()).isEqualByComparingTo("30");
    }

    @Test
    void duasLinhas_viramUmVolumeSo_somandoPesoEEmpilhandoAAltura() {
        // P: 0,3 kg, 20x2x30  |  G: 0,5 kg, 25x3x35
        MeVolume v = MeVolume.agrupando(List.of(
                item("0.3", "20", "2", "30", 1),
                item("0.5", "25", "3", "35", 1)));

        assertThat(v.weight()).isEqualByComparingTo("0.8");
        assertThat(v.width()).isEqualByComparingTo("25");       // o maior
        assertThat(v.length()).isEqualByComparingTo("35");      // o maior
        assertThat(v.height()).isEqualByComparingTo("5");       // 2 + 3
    }

    @Test
    void quantidadeMultiplicaPesoEAltura() {
        MeVolume v = MeVolume.agrupando(List.of(item("0.3", "20", "2", "30", 3)));

        assertThat(v.weight()).isEqualByComparingTo("0.9");
        assertThat(v.height()).isEqualByComparingTo("6");
        assertThat(v.width()).isEqualByComparingTo("20");
    }
}
