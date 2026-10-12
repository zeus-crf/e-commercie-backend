package com.ecommercie.pedido.cep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Confere o CEP do checkout num servico de CEP. A cotacao do Melhor Envio aceita qualquer CEP
 * de 8 digitos (ate 99999999); o ME so recusa no carrinho, quando o admin vai gerar a etiqueta
 * de um pedido ja pago. Aqui o cliente descobre na hora.
 *
 * Se o servico de CEP estiver fora do ar, deixa passar: nao trava a venda por causa dele.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidadorCep {

    private final ConsultaCep consultaCep;

    public void validar(String cep, String uf) {
        ResultadoCep resultado = consultaCep.consultar(cep);
        switch (resultado.situacao()) {
            case INEXISTENTE -> throw new IllegalArgumentException(
                    "CEP " + cep + " não encontrado. Confira o CEP do endereço de entrega.");
            case ENCONTRADO -> {
                if (!resultado.uf().equalsIgnoreCase(uf)) {
                    throw new IllegalArgumentException(
                            "O CEP " + cep + " é de " + resultado.uf() + ", mas a UF informada é " + uf + ".");
                }
            }
            case INDISPONIVEL -> log.warn("Checkout seguiu sem validar o CEP {} (servico de CEP indisponivel)", cep);
        }
    }
}
