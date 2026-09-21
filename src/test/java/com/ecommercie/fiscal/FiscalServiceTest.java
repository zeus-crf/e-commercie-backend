package com.ecommercie.fiscal;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.model.InventoryItem;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.fiscal.dto.FiscalDocument;
import com.ecommercie.fiscal.enums.FiscalDocumentType;
import com.ecommercie.fiscal.enums.InvoiceStatus;
import com.ecommercie.fiscal.model.Invoice;
import com.ecommercie.fiscal.repository.InvoiceRepository;
import com.ecommercie.fiscal.service.FiscalService;
import com.ecommercie.outbox.OutboxTypes;
import com.ecommercie.outbox.repository.OutboxEventRepository;
import com.ecommercie.pedido.models.Address;
import com.ecommercie.pedido.models.Order;
import com.ecommercie.pedido.models.OrderItem;
import com.ecommercie.pedido.repository.OrderRepository;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import com.ecommercie.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FiscalServiceTest {

    @Autowired FiscalService fiscalService;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired CartRepository cartRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired DatabaseCleaner databaseCleaner;
    @MockitoBean
    FiscalProvider fiscalProvider;


    private final AtomicInteger seq = new AtomicInteger();

    // Limpa na ordem das FKs: invoice referencia pedido, pedido referencia usuario/produto.
    @BeforeEach
    void limpar() {
        databaseCleaner.limparTudo();
    }

    private Order pedidoPadrao() {
        int n = seq.incrementAndGet();

        User user = userRepository.save(User.builder()
                .nome("Cliente Fiscal " + n)
                .email("fiscal" + n + "@teste.com")
                .senha("$2a$10$naoimporta")
                .cpfCnpj("12345678909")
                .papel(Papel.CLIENTE)
                .ativo(true)
                .build());

        Category categoria = categoryRepository.save(Category.builder()
                .nome("Categoria " + n)
                .slug("categoria-" + n)
                .build());

        Product produto = productRepository.save(Product.builder()
                .category(categoria)
                .nome("Camiseta preta P")
                .preco(new BigDecimal("150.00"))
                .ativo(true)
                .pesoKg(new BigDecimal("0.3"))
                .alturaCm(new BigDecimal("2"))
                .larguraCm(new BigDecimal("20"))
                .comprimentoCm(new BigDecimal("30"))
                .build());

        inventoryItemRepository.save(InventoryItem.builder()
                .product(produto)
                .disponivel(10)
                .reservada(0)
                .build());

        Address endereco = Address.builder()
                .logradouro("Rua das Flores")
                .numero("100")
                .bairro("Centro")
                .cidade("Sao Paulo")
                .uf("SP")
                .cep("01310100")
                .build();

        Order pedido = Order.builder()
                .user(user)
                .address(endereco)          // cascade ALL: salva junto com o pedido
                .valorItens(new BigDecimal("300.00"))
                .valorFrete(new BigDecimal("25.90"))
                .shippingServiceId(2)
                .build();

        Invoice invoice = Invoice.builder()
                .order(pedido)
                        .tipo(FiscalDocumentType.DCE)
                                .chave("chave")
                                        .status(InvoiceStatus.AUTORIZADO)
                                                .build();

        // snapshot do item, igual ao que o checkout grava
        pedido.getItens().add(OrderItem.builder()
                .order(pedido)
                .product(produto)
                .nomeProduto(produto.getNome())
                .precoUnitario(produto.getPreco())
                .quantidade(2)
                .ncm("61091000")
                .cfop("6108")
                .origem("0")
                .build());

        pedido.markPaid();

        return orderRepository.save(pedido);
    }

    @Test
    public void pedidoNaoEncontrado() throws EntityNotFoundException {
        assertThrows(EntityNotFoundException.class, () -> {
            fiscalService.emitirParaPedido("9r89893294");
        });
    }

    @Test
    void naoReemiteQuandoJaAutorizada() {
        Order pedido = pedidoPadrao();
        invoiceRepository.save(Invoice.builder()
                .order(pedido)
                .tipo(FiscalDocumentType.DCE)
                .chave("chave-original")
                .status(InvoiceStatus.AUTORIZADO)
                .build());

        fiscalService.emitirParaPedido(pedido.getId());

        verify(fiscalProvider, never()).emitir(any());
        assertThat(invoiceRepository.count()).isEqualTo(1);
        assertThat(invoiceRepository.findByOrderId(pedido.getId()))
                .get().extracting(Invoice::getChave).isEqualTo("chave-original");
    }

    @Test
    void invoiceNaoAutorizada(){
        Order pedido = pedidoPadrao();

        Invoice invoiceOriginal = invoiceRepository.save(
                Invoice.builder()
                        .order(pedido)
                        .tipo(FiscalDocumentType.DCE)
                        .chave("chave-original")
                        .status(InvoiceStatus.PENDENTE)
                        .build()
        );

        when(fiscalProvider.emitir(any(Order.class)))
                .thenReturn(new FiscalDocument(FiscalDocumentType.NFE, "123"));

        fiscalService.emitirParaPedido(pedido.getId());

        Invoice invoiceAtualizada = invoiceRepository
                .findByOrderId(pedido.getId())
                .orElseThrow();

        assertThat(invoiceAtualizada.getId())
                .isEqualTo(invoiceOriginal.getId());

        assertThat(invoiceRepository.findByOrderId(pedido.getId()))
                .get()
                .satisfies(invoice -> {

                    assertThat(invoice.getOrder().getId())
                            .isEqualTo(pedido.getId());

                    assertThat(invoice.getStatus())
                            .isEqualTo(InvoiceStatus.AUTORIZADO);

                    assertThat(invoice.getChave())
                            .isEqualTo("123");
                });
    }


    @Test
    void providerNOOP(){
        Order pedido = pedidoPadrao();

        when(fiscalProvider.emitir(any(Order.class)))
                .thenReturn(new FiscalDocument(FiscalDocumentType.NOOP, "123"));

        fiscalService.emitirParaPedido(pedido.getId());

        assertThat(invoiceRepository.findByOrderId(pedido.getId())).get().satisfies(inv -> {
            assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.NOOP);
            assertThat(inv.getChave()).isEqualTo("123");
            assertThat(inv.getTipo())
                    .isEqualTo(FiscalDocumentType.NOOP);
        });

    }


    @Test
    void criaSalvaInvoice(){
        Order pedido = pedidoPadrao();
        when(fiscalProvider.emitir(any(Order.class)))
                .thenReturn(new FiscalDocument(FiscalDocumentType.DCE, "123456"));

        fiscalService.emitirParaPedido(pedido.getId());

        assertThat(invoiceRepository.findByOrderId(pedido.getId())).get().satisfies(inv -> {
            assertThat(inv.getOrder().getId()).isEqualTo(pedido.getId());
                    assertThat(inv.getTipo()).isEqualTo(FiscalDocumentType.DCE);
                        assertThat(inv.getChave()).isEqualTo("123456");
                        assertThat(inv.getStatus()).isEqualTo(InvoiceStatus.AUTORIZADO);
        });

    }

    @Test
    void buscarPedidoId(){
        Order pedido = pedidoPadrao();
        when(fiscalProvider.emitir(any(Order.class)))
                .thenReturn(new FiscalDocument(FiscalDocumentType.DCE, "123"));

        fiscalService.emitirParaPedido(pedido.getId());

        Optional<Invoice> resultado = fiscalService.buscarPorPedido(pedido.getId());

        assertThat(resultado)
                .isPresent()
                .get()
                .satisfies(inv -> {
                    assertThat(inv.getOrder().getId())
                            .isEqualTo(pedido.getId());

                    assertThat(inv.getTipo())
                            .isEqualTo(FiscalDocumentType.DCE);

                    assertThat(inv.getChave())
                            .isEqualTo("123");
                });
    }

    @Test
    void deveAgendarEmissao() {
        Order pedido = pedidoPadrao();

        fiscalService.agendarEmissao(pedido.getId());

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .satisfies(ev -> {
                    assertThat(ev.getType()).isEqualTo(OutboxTypes.FISCAL_EMISSAO);
                    assertThat(ev.getPayload()).contains(pedido.getId());
                });
    } 


}
