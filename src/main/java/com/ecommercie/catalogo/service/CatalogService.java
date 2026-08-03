package com.ecommercie.catalogo.service;

import com.ecommercie.catalogo.dtos.ProductRequest;
import com.ecommercie.catalogo.dtos.ProductResponse;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Pageable pageable) {
        return productRepository.buscar(null, null, null, null, true, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse listById(String productId) {
        return productRepository.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {

        if (productRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new IllegalArgumentException("Este produto já existe");
        }

        var category = categoryRepository.findById(request.category_id())
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        Product product = Product.builder()
                .category(category)
                .nome(request.nome())
                .preco(request.preco())
                .pesoKg(request.peso_kg())
                .alturaCm(request.altura_cm())
                .larguraCm(request.largura_cm())
                .comprimentoCm(request.comprimento_cm())
                .ncm(request.ncm())
                .cfop(request.cfop())
                .origem(request.origem())
                .build();

        productRepository.save(product);

        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse editProduct(String productId, ProductRequest request) {

        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Esse produto não foi encontrado"));

        if (request.nome() != null) product.setNome(request.nome());
        if (request.category_id() != null) {
            var category = categoryRepository.findById(request.category_id())
                    .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));
            product.setCategory(category);
        }
        if (request.preco() != null) product.setPreco(request.preco());
        if (request.peso_kg() != null) product.setPesoKg(request.peso_kg());
        if (request.altura_cm() != null) product.setAlturaCm(request.altura_cm());
        if (request.largura_cm() != null) product.setLarguraCm(request.largura_cm());
        if (request.comprimento_cm() != null) product.setComprimentoCm(request.comprimento_cm());
        if (request.ncm() != null) product.setNcm(request.ncm());
        if (request.cfop() != null) product.setCfop(request.cfop());
        if (request.origem() != null) product.setOrigem(request.origem());

        productRepository.save(product);

        return ProductResponse.from(product);
    }

    @Transactional
    public void deleteProduct(String productId) {
        productRepository.deleteById(productId);
    }
}
