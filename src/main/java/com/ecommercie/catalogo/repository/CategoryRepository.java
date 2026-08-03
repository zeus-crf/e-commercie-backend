package com.ecommercie.catalogo.repository;

import com.ecommercie.catalogo.models.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, String> {

    Page<Category> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}
