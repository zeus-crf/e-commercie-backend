package com.ecommercie.support;

import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.models.ProductVariant;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.catalogo.repository.ProductVariantRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


/**
 * Produto + variacao + estoque para os testes. Concentra aqui o que muda quando o modelo de catalogo muda
 * (variacoes, estoque por variacao), em vez de nove helpers iguais espalhados.
 */
@Component
@RequiredArgsConstructor
public class CatalogoDeTeste {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public record Item(Product product, ProductVariant variacao) {
        public String productId() { return  product.getId(); };
        public String variantId() { return  variacao.getId(); };
    }

    /** Produto ativo (0,3 kg; 2x20x30 cm) com a variacao "unica" e estoque. */
    public Item produto(String nome, String descricao, String preco, int disponivel, int reservada) {
        long n = System.nanoTime();
        Category cat = categoryRepository.save(Category.builder().nome("Cat " + n).slug("cat-" + n).build());
        Product p = productRepository.save(Product.builder()
                .category(cat).nome(nome).descricao(descricao).preco(new BigDecimal(preco)).ativo(true)
                .pesoKg(new BigDecimal("0.3")).alturaCm(new BigDecimal("2"))
                .larguraCm(new BigDecimal("20")).comprimentoCm(new BigDecimal("30"))
                .build());
        ProductVariant v = variantRepository.save(ProductVariant.builder().product(p).build());
        inventoryItemRepository.save(InventoryItem.builder()
                .product(p).disponivel(disponivel).reservada(reservada).build());
        return new Item(p, v);
    }

    public Item produto(String nome, String preco, int disponivel) {
        return produto(nome, null, preco, disponivel, 0);
    }
}
