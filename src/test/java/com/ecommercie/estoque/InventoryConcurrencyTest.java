package com.ecommercie.estoque;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.estoque.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de concorrência do estoque (Fase 4) — a prova do lock pessimista.
 *
 * Cenário: 1 única unidade disponível e 2 threads tentando reservar ao mesmo tempo.
 * Com o SELECT ... FOR UPDATE (via lockForUpdate), as transações são serializadas:
 * a 2ª espera a 1ª commitar, relê reservada=1 e falha. Resultado esperado:
 * exatamente 1 reserva vence e o estoque NUNCA fica negativo.
 *
 * Precisa de Postgres real (Testcontainers). H2 NÃO reproduz o FOR UPDATE — por isso
 * o mesmo @Import(TestcontainersConfiguration) dos outros testes de integração.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InventoryConcurrencyTest {

    @Autowired InventoryService inventoryService;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    // limpa na ordem das FKs: estoque -> produtos -> categorias
    @BeforeEach
    void limpar() {
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private String criarProdutoComEstoque(int disponivel) {
        Category cat = categoryRepository.save(
                Category.builder().nome("Camisetas").slug("camisetas").build());

        Product product = productRepository.save(Product.builder()
                .category(cat)
                .nome("Camiseta Única")
                .preco(new BigDecimal("99.90"))
                .ativo(true)
                .pesoKg(new BigDecimal("0.3"))
                .alturaCm(new BigDecimal("2.0"))
                .larguraCm(new BigDecimal("20.0"))
                .comprimentoCm(new BigDecimal("30.0"))
                .build());

        inventoryItemRepository.save(InventoryItem.builder()
                .product(product)
                .disponivel(disponivel)
                .reservada(0)
                .build());

        return product.getId();
    }

    @Test
    void reservarConcorrente_ultimaUnidade_apenasUmaReservaVence() throws Exception {
        String productId = criarProdutoComEstoque(1);   // só 1 disponível

        int threads = 2;
        var largada = new CountDownLatch(1);            // porteira: solta as duas juntas
        var terminaram = new CountDownLatch(threads);
        var sucessos = new AtomicInteger();
        var falhas = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    largada.await();                     // espera o sinal
                    inventoryService.reservarItem(productId, 1);
                    sucessos.incrementAndGet();
                } catch (Exception e) {
                    falhas.incrementAndGet();            // estoque insuficiente (o perdedor)
                } finally {
                    terminaram.countDown();
                }
            });
        }

        largada.countDown();                             // 🏁 as duas correm ao mesmo tempo
        boolean acabou = terminaram.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(acabou).isTrue();                     // não travou (sem deadlock)
        assertThat(sucessos.get()).isEqualTo(1);         // exatamente 1 reservou
        assertThat(falhas.get()).isEqualTo(1);           // exatamente 1 falhou

        InventoryItem item = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertThat(item.getReservada()).isEqualTo(1);    // reservou 1, não 2
        assertThat(item.getDisponivel()).isEqualTo(1);   // físico intacto (baixa é no pagamento)
    }

    @Test
    void reservar_semEstoque_falha() {
        String productId = criarProdutoComEstoque(0);   // esgotado

        assertThat(catchException(() -> inventoryService.reservarItem(productId, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        InventoryItem item = inventoryItemRepository.findByProductId(productId).orElseThrow();
        assertThat(item.getReservada()).isEqualTo(0);    // nada foi reservado
    }

    // pequeno helper para capturar a exceção sem try/catch inline
    private static Exception catchException(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
