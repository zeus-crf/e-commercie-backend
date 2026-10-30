package com.ecommercie.estoque.service;

import com.ecommercie.catalogo.models.ProductVariant;
import com.ecommercie.catalogo.repository.ProductVariantRepository;
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
    private final ProductVariantRepository variantRepository;

    @Transactional
    public void reservarItem(String variantId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Este produto não existe"));

        if (item.getDisponivel() - item.getReservada() < qtd ) {
            throw new IllegalArgumentException("Esse produto não tem itens em estoque");
        }

        item.setReservada(item.getReservada() + qtd);
    }


    @Transactional
    public void confirmarBaixa(String variantId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Este produto não foi encontrado"));

        if (item.getReservada() < qtd) {
            throw new IllegalStateException("Baixa maior que o reservado");
        }

        item.setReservada(item.getReservada() - qtd);
        item.setDisponivel(item.getDisponivel() - qtd);

    }

    @Transactional
    public void estornarBaixa(String variantId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado"));


        item.setDisponivel(item.getDisponivel() + qtd);
    }


    @Transactional
    public void devolverReserva(String variantId, int qtd) {
        var item = inventoryItemRepository.lockForUpdate(variantId)
                .orElseThrow(() -> new EntityNotFoundException("Esse produto não foi encontrado"));

        if (item.getReservada() < qtd) {
            throw new IllegalStateException("Devolução maior do que o reservado");
        }


        item.setReservada(item.getReservada() - qtd);
    }

    @Transactional
    public void criarZerado(ProductVariant variant) {
        InventoryItem item = InventoryItem.builder()
                .variant(variant)
                .disponivel(0)
                .reservada(0)
                .build();

        inventoryItemRepository.save(item);
    }

    @Transactional
    public void ajustarEstoque(String variantId, int estoque) {
        var item = inventoryItemRepository.findByVariantId(variantId)
                .orElse(null);

        if (item == null) {
            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new EntityNotFoundException("Variação não encontrada"));
            InventoryItem novo = InventoryItem.builder()
                    .variant(variant)
                    .disponivel(estoque)
                    .reservada(0)
                    .build();
            inventoryItemRepository.save(novo);
            return;
        }

        if (item.getReservada() > estoque) {
            throw new IllegalArgumentException("O estoque não pode ser menor que o já reservado");
        }

        item.setDisponivel(estoque);
    }

    /** Remove o estoque de um produto (usado quando o produto é excluído). */
    @Transactional
    public void removerDoProduto(String productId) {
        inventoryItemRepository.deleteAll(inventoryItemRepository.findByVariantProductId(productId));
    }
}
