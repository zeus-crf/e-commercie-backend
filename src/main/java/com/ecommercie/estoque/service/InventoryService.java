package com.ecommercie.estoque.service;

import com.ecommercie.catalogo.models.Product;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public void reservarItem(String productId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Este produto não existe"));

        if (item.getDisponivel() - item.getReservada() < qtd ) {
            throw new IllegalArgumentException("Esse produto não tem itens em estoque");
        }

        item.setReservada(item.getReservada() + qtd);
    }


    @Transactional
    public void confirmarBaixa(String productId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Este produto não foi encontrado"));

        if (item.getReservada() < qtd) {
            throw new IllegalStateException("Baixa maior que o reservado");
        }

        item.setReservada(item.getReservada() - qtd);
        item.setDisponivel(item.getDisponivel() - qtd);

    }


    @Transactional
    public void devolverReserva(String productId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Esse produto não foi encontrado"));

        if (item.getReservada() < qtd) {
            throw new IllegalStateException("Devolução maior do que o reservado");
        }


        item.setReservada(item.getReservada() - qtd);
    }

    @Transactional
    public void criarZerado(Product product) {
        InventoryItem item = InventoryItem.builder()
                .product(product)
                .disponivel(0)
                .reservada(0)
                .build();

        inventoryItemRepository.save(item);
    }

    @Transactional
    public void ajustarEstoque(String productId, int estoque) {
        var item = inventoryItemRepository.lockForUpdate(productId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));

        if (item.getReservada() > estoque) {
            throw new IllegalArgumentException("O estoque não pode ser menor que o já reservado");
        }

        item.setDisponivel(estoque);
    }



}
