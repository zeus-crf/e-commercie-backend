package com.ecommercie.catalogo.service;

import com.ecommercie.catalogo.dtos.CategoryRequest;
import com.ecommercie.catalogo.dtos.CategoryResponse;
import com.ecommercie.catalogo.dtos.ProductRequest;
import com.ecommercie.catalogo.dtos.ProductResponse;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.models.ProductImage;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.catalogo.storage.StorageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    private final StorageService storageService;

    @Transactional(readOnly = true)
    public Page<ProductResponse> listProducts(Pageable pageable) {
        return productRepository.buscar(null, null, null, null, true, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse listByIdProduct(String productId) {
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

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategory(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse listByIdCategory(String categoryId) {
        return categoryRepository.findById(categoryId)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));
    }


    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {

        if (categoryRepository.existsByNomeIgnoreCase(request.nome())){
            throw new IllegalArgumentException("Essa categoria já existe");
        }

        if (categoryRepository.existsBySlugIgnoreCase(request.slug())){
            throw new IllegalArgumentException("Esse slug já existe");
        }



        Category category = Category.builder()
                .nome(request.nome())
                .slug(request.slug())
                .build();

        categoryRepository.save(category);

        return CategoryResponse.from(category);

    }

    @Transactional
    public CategoryResponse editCategory(String categoryId,CategoryRequest request) {
        var category  = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Essa categoria não existe"));

        if (request.nome() != null) category.setNome(request.nome());
        if (request.slug() != null) category.setSlug(request.slug());

        categoryRepository.save(category);

        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(String categoryId) {
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new IllegalArgumentException("Categoria com produtos não pode ser excluída");
        }
        categoryRepository.deleteById(categoryId);
    }

    @Transactional
    public ProductResponse adicionarImagem(String productId, MultipartFile arquivo) {

        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));


        var url = storageService.store(arquivo);

        int proximaOrdem = product.getImagens().size();

        ProductImage imagem = ProductImage.builder()
                .product(product)
                .ordem(proximaOrdem)
                .url(url)
                .build();

        product.getImagens().add(imagem);

        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse deletarImagem(String productId, String url) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        var imagem = product.getImagens().stream()
                .filter(img -> img.getUrl().equals(url))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("A imagem não foi encontrada"));

        storageService.delete(imagem.getUrl());
        product.getImagens().remove(imagem);

        return ProductResponse.from(product);
    }
}
