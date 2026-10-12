package com.ecommercie.pedido.cep;

/**
 * Resposta da consulta de CEP. INDISPONIVEL = o servico nao respondeu (fora do ar, timeout):
 * nao da para afirmar nada sobre o CEP.
 */
public record ResultadoCep(Situacao situacao, String uf) {

    public enum Situacao { ENCONTRADO, INEXISTENTE, INDISPONIVEL }

    public static ResultadoCep encontrado(String uf) {
        return new ResultadoCep(Situacao.ENCONTRADO, uf);
    }

    public static ResultadoCep inexistente() {
        return new ResultadoCep(Situacao.INEXISTENTE, null);
    }

    public static ResultadoCep indisponivel() {
        return new ResultadoCep(Situacao.INDISPONIVEL, null);
    }
}
