package com.ecommercie.support;

import com.ecommercie.mercado_pago.PaymentGateway;
import com.ecommercie.mercado_pago.dtos.PaymentPreference;
import com.ecommercie.mercado_pago.dtos.PreferenciaRequest;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stub do gateway de pagamento: nao toca a rede. Preferencia deterministica pelo id do pedido.
 * Registra o que recebeu e se havia transacao ativa na chamada. O DatabaseCleaner chama reset().
 */
public class StubPaymentGateway implements PaymentGateway {

    public static final String PREFERENCE_ID_PREFIX = "stub-pref-";

    private final List<PreferenciaRequest> preferencias = new CopyOnWriteArrayList<>();
    private final List<Boolean> transacaoAtivaNasChamadas = new CopyOnWriteArrayList<>();

    @Override
    public PaymentPreference criarPreferencia(PreferenciaRequest request) {
        transacaoAtivaNasChamadas.add(TransactionSynchronizationManager.isActualTransactionActive());
        preferencias.add(request);
        return new PaymentPreference(
                PREFERENCE_ID_PREFIX + request.orderId(),
                "https://stub.local/checkout/" + request.orderId());
    }

    public void reset() {
        preferencias.clear();
        transacaoAtivaNasChamadas.clear();
    }

    public List<PreferenciaRequest> preferencias() { return preferencias; }
    public List<Boolean> transacaoAtivaNasChamadas() { return transacaoAtivaNasChamadas; }
}
