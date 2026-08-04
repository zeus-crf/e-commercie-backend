package com.ecommercie.catalogo.repository;

import com.ecommercie.catalogo.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, String> {

    @Override
    Page<Product> findAll(Pageable pageable);

    boolean existsByNomeIgnoreCase(String nome);

    Page<Product> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    Page<Product> findAllByCategoryId(String categoryId, Pageable pageable);

    /**
     * Busca combinada do catálogo — todos os filtros são opcionais (null = ignora).
     * apenasAtivos=true limita aos produtos ativos (catálogo público);
     * o admin passa false para ver inclusive os inativos.
     */
    @Query("""
            SELECT p FROM Product p
            WHERE (CAST(:q AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
              AND (CAST(:categoryId AS string) IS NULL OR p.category.id = :categoryId)
              AND (CAST(:minPreco AS big_decimal) IS NULL OR p.preco >= :minPreco)
              AND (CAST(:maxPreco AS big_decimal) IS NULL OR p.preco <= :maxPreco)
              AND (:apenasAtivos = FALSE OR p.ativo = TRUE)
            """)
    Page<Product> buscar(@Param("q") String q,
                         @Param("categoryId") String categoryId,
                         @Param("minPreco") BigDecimal minPreco,
                         @Param("maxPreco") BigDecimal maxPreco,
                         @Param("apenasAtivos") boolean apenasAtivos,
                         Pageable pageable);

    boolean existsByCategoryId(String categoryId);
}
