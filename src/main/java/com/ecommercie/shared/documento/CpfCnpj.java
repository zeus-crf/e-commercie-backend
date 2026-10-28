package com.ecommercie.shared.documento;

/**
 * CPF (11 digitos) ou CNPJ (14 digitos), com ou sem mascara. Valida pelos digitos verificadores;
 * sequencias repetidas (00000000000) passam no calculo mas nao sao documentos reais.
 */
public final class CpfCnpj {

    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CpfCnpj() {

    }

    public static String somenteDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    public static boolean valido(String valor) {
        String d = somenteDigitos(valor);
        if (d == null || d.chars().distinct().count() <= 1) {
            return false;   // vazio ou tudo igual
        }
        return switch (d.length()) {
            case 11 -> digito(d, pesosDecrescentes(10, 9)) == d.charAt(9) - '0'
                    && digito(d, pesosDecrescentes(11, 10)) == d.charAt(10) - '0';
            case 14 -> digito(d, PESOS_CNPJ_1) == d.charAt(12) - '0'
                    && digito(d, PESOS_CNPJ_2) == d.charAt(13) - '0';
            default -> false;
        };
    }

    // CPF: pesos de `inicial` ate 2 sobre os primeiros `quantidade` digitos
    private static int[] pesosDecrescentes(int inicial, int quantidade) {
        int[] pesos = new int[quantidade];
        for (int i = 0; i < quantidade; i++) {
            pesos[i] = inicial - i;
        }
        return pesos;
    }
    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

}
