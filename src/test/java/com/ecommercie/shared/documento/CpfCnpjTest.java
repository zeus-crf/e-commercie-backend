package com.ecommercie.shared.documento;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CpfCnpjTest {

    @Test
    void cfpValido_comOuSemMascara() {
        assertThat(CpfCnpj.valido("12345678909")).isTrue();
        assertThat(CpfCnpj.valido("123.456.789-09")).isTrue();
        assertThat(CpfCnpj.valido("111.444.777-35")).isTrue();
    }

    @Test
    void cpfComDigitoErrado_eInvalido() {
        assertThat(CpfCnpj.valido("12345678900")).isFalse();
        assertThat(CpfCnpj.valido("123.456.789-08")).isFalse();
    }

    @Test
    void cnpjValido_eValido() {
        assertThat(CpfCnpj.valido("00000000000")).isFalse();
        assertThat(CpfCnpj.valido("11111111111")).isFalse();
        assertThat(CpfCnpj.valido("00000000000000")).isFalse();
    }

    @Test
    void cnpjValido_eInvalido() {
        assertThat(CpfCnpj.valido("11.222.333/0001-81")).isTrue();
        assertThat(CpfCnpj.valido("11222333000180")).isFalse();
    }

    @Test
    void tamanhoErrado_ouVazio_eInvalido() {
        assertThat(CpfCnpj.valido("1234567890")).isFalse();
        assertThat(CpfCnpj.valido("123456789012")).isFalse();
        assertThat(CpfCnpj.valido("")).isFalse();
        assertThat(CpfCnpj.valido(null)).isFalse();
    }

    @Test
    void somenteDigitos_tiraAMascara() {
        assertThat(CpfCnpj.somenteDigitos("123.456.789-09")).isEqualTo("12345678909");
        assertThat(CpfCnpj.somenteDigitos("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(CpfCnpj.somenteDigitos(null)).isNull();
    }

    @Test
    void anotacao_aceitaValido_eRecusaInvalido() {
        CpfCnpjValidoValidator validator = new CpfCnpjValidoValidator();
        assertThat(validator.isValid("123.456.789-09", null)).isTrue();
        assertThat(validator.isValid("12345678900", null)).isFalse();
        assertThat(validator.isValid(null, null)).isTrue();   // obrigatoriedade fica com o @NotBlank
    }

}