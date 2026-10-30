package com.ecommercie.catalogo.service;

import com.ecommercie.catalogo.dtos.CategoryRequest;
import com.ecommercie.catalogo.dtos.CategoryResponse;
import com.ecommercie.catalogo.dtos.ProductRequest;
import com.ecommercie.catalogo.dtos.ProductResponse;
import com.ecommercie.catalogo.dtos.VariantRequest;
import com.ecommercie.catalogo.dtos.VariantResponse;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.models.ProductImage;
import com.ecommercie.catalogo.models.ProductVariant;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.catalogo.repository.ProductVariantRepository;
import com.ecommercie.catalogo.storage.StorageService;
import com.ecommercie.estoque.service.InventoryService;
import com.ecommercie.shared.ConflitoException;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ProductVariantRepository variantRepository;
 

    private final InventoryService inventoryService;

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


        List<VariantRequest> pedidas = request.variacoes() == null || request.variacoes().isEmpty()
            ? List.of(new VariantRequest(null, null, null, null, null, null, null, null, null))
                : request.variacoes();
        Set<String> combinacoes = new HashSet<>();
        for (VariantRequest vr : pedidas) {
            ProductVariant v = novaVariacao(product, vr);
            if (!combinacoes.add(descricao(v.getTamanho(), v.getCor()))) {
                throw new ConflitoException("Variação " + descricao(v.getTamanho(), v.getCor()) + " repetida no cadastro");
            }
            product.getVariacoes().add(v);
        }
        productRepository.save(product);
        product.getVariacoes().forEach(inventoryService::criarZerado);

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
        inventoryService.removerDoProduto(productId);   // remove o estoque antes (FK)
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

    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAllCategory(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(CategoryResponse::from);
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


    @Transactional 
    public VariantResponse criarVariacao(String productId, VariantRequest request) {

        var product = productRepository.findById(productId)
            .orElseThrow(() ->new EntityNotFoundException("Esse produto não existe"));

        // persiste a variacao direto: saveAndFlush(product) faria merge e devolveria uma copia, deixando `v` transiente
        ProductVariant v = variantRepository.saveAndFlush(novaVariacao(product, request));
        product.getVariacoes().add(v);
        inventoryService.criarZerado(v);
        return VariantResponse.from(v);
    }

    @Transactional
    public VariantResponse editarVariacao(String variantId, VariantRequest request) {
        var v = variantRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Variação não encontrada"));

        String tamanho = request.tamanho() != null ? normalizarTamanho(request.tamanho()) : v.getTamanho();
        String cor = request.cor() != null ? normalizarTexto(request.cor()) : v.getCor();
        boolean mudouCombinacao = !Objects.equals(tamanho, v.getTamanho()) || !Objects.equals(cor, v.getCor());
        if (mudouCombinacao && variantRepository.existsByProductIdAndTamanhoAndCor(v.getProduct().getId(), tamanho, cor)) {
            throw new ConflitoException("Já existe uma variação " + descricao(tamanho, cor) + " neste produto");
        }
        String sku = request.sku() != null ? normalizarTexto(request.sku()) : v.getSku();
        if (sku != null && !sku.equals(v.getSku()) && variantRepository.existsBySku(sku)) {
            throw new ConflitoException("O SKU " + sku + " já está em uso");
        }

        v.setTamanho(tamanho);
        v.setCor(cor);
        v.setSku(sku);
        if (request.preco() != null) v.setPreco(request.preco());
        if (request.peso_kg() != null) v.setPesoKg(request.peso_kg());
        if (request.altura_cm() != null) v.setAlturaCm(request.altura_cm());
        if (request.largura_cm() != null) v.setLarguraCm(request.largura_cm());
        if (request.comprimento_cm() != null) v.setComprimentoCm(request.comprimento_cm());
        if (request.ativo() != null) v.setAtivo(request.ativo());
        return VariantResponse.from(v);
    }
    


    private ProductVariant novaVariacao(Product product, VariantRequest request) {
       String tamanho = normalizarTamanho(request.tamanho());
       String cor = normalizarTexto(request.cor());
       String sku = normalizarTexto(request.sku());

       if (product.getId() != null && variantRepository.existsByProductIdAndTamanhoAndCor(product.getId(), tamanho, cor)) {
            throw new ConflitoException("Já existe uma variação " + descricao(tamanho, cor) + " neste produto");
       }
       if (sku != null && variantRepository.existsBySku(sku)) {
            throw new ConflitoException("O SKU " + sku + " já está em uso");
       }

       return ProductVariant.builder()
        .product(product).sku(sku).tamanho(tamanho).cor(cor).preco(request.preco())
        .pesoKg(request.peso_kg()).alturaCm(request.altura_cm())
        .larguraCm(request.largura_cm()).comprimentoCm(request.comprimento_cm())
        .ativo(request.ativo() == null || request.ativo())
        .build();
    }

    private static String normalizarTexto(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalizarTamanho(String s) {
        String t = normalizarTexto(s);
        return t == null ? null : t.toUpperCase();
    }

    private static String descricao(String tamanho, String cor) {
        String d = Stream.of(tamanho, cor).filter(Objects::nonNull).collect(Collectors.joining(" / "));
        return d.isEmpty() ? "única" : d;
    }
}
