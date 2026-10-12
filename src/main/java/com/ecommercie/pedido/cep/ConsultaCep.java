package com.ecommercie.pedido.cep;

/**
 * Consulta se um CEP existe e a qual UF pertence. Interface para o checkout nao depender
 * do servico concreto (ViaCEP) e para os testes trocarem por stub/mock.
 */
public interface ConsultaCep {

    ResultadoCep consultar(String cep);
}
