package com.ecommercie.support;

import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.fiscal.repository.InvoiceRepository;
import com.ecommercie.melhor_envio.repository.ShipmentTrackingEventRepository;
import com.ecommercie.melhor_envio.repository.ShippimentRepository;
import com.ecommercie.mercado_pago.WebhookEventRepository;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Limpeza do banco entre testes, na ordem correta das foreign keys.
 *
 * POR QUE ISTO EXISTE: as classes de teste compartilham o mesmo container Postgres, e a ordem em
 * que o Surefire as executa MUDA entre sistemas operacionais. Uma classe que apaga apenas as suas
 * tabelas quebra quando a classe anterior deixou linhas apontando para elas — e o sintoma aparece
 * so no CI, com o nome de uma classe que nao tem nada a ver com a mudanca.
 *
 * Cada classe nova com FK nova precisa ser acrescentada AQUI, e nao em oito @BeforeEach diferentes.
 */
@Component
@RequiredArgsConstructor
public class DatabaseCleaner {

    private final ShipmentTrackingEventRepository shipmentTrackingEventRepository;
    private final ShippimentRepository shippimentRepository;
    private final InvoiceRepository invoiceRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    /** Apaga tudo, das folhas para as raizes. */
    public void limparTudo() {
        shipmentTrackingEventRepository.deleteAll();  // -> envios
        shippimentRepository.deleteAll();             // -> pedido
        invoiceRepository.deleteAll();                // -> pedido
        webhookEventRepository.deleteAll();           // sem FK
        outboxEventRepository.deleteAll();            // sem FK
        orderRepository.deleteAll();                  // cascata: pedido_item + endereco
        cartRepository.deleteAll();                   // cascata: carrinho_item
        inventoryItemRepository.deleteAll();          // -> produtos
        productRepository.deleteAll();                // cascata: produto_imagens
        categoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();           // -> usuarios
        userRepository.deleteAll();
    }
}
