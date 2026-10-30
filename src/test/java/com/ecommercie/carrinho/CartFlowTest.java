package com.ecommercie.carrinho;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.catalogo.repository.ProductVariantRepository;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import com.ecommercie.support.CatalogoDeTeste;
import com.ecommercie.support.DatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do carrinho (Fase 5).
 *
 * Foco:
 * (1) SEGURANÇA — carrinho é isolado por usuário: A não vê nem mexe no carrinho
 * de B (IDOR);
 * sem login -> 401.
 * (2) Preço do subtotal vem do CATÁLOGO (servidor), não do cliente.
 * (3) Fluxo add / soma / edit / remove.
 *
 * Mesmo setup dos outros: Postgres real (Testcontainers), MockMvc com o
 * Security aplicado.
 * O usuário logado é injetado via @AuthenticationPrincipal no controller; aqui
 * simulamos
 * o login com o cookie do /auth/register.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CartFlowTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        CartRepository cartRepository;
        @Autowired
        ProductRepository productRepository;
        @Autowired
        CategoryRepository categoryRepository;
        @Autowired
        InventoryItemRepository inventoryItemRepository;
        @Autowired
        UserRepository userRepository;
        @Autowired
        RefreshTokenRepository refreshTokenRepository;
        @Autowired
        DatabaseCleaner databaseCleaner;
        @Autowired
        ProductVariantRepository variantRepository;
        @Autowired
        CatalogoDeTeste catalogo;

        // limpa na ordem das FKs: carrinho (cascade nos itens) -> estoque -> produtos
        // -> categorias -> usuarios
        @BeforeEach
        void limpar() {
                databaseCleaner.limparTudo();
        }

        // ----------------- helpers -----------------

        private Cookie clienteCookie(String email) throws Exception {
                String body = """
                                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678909" }
                                """.formatted(email);
                return mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                                .andReturn().getResponse().getCookie("access_token");
        }

        private CatalogoDeTeste.Item saveProduct(String nome, String preco) {
                return catalogo.produto(nome, preco, 10);
        }

        private String itemBody(String variantId, int qtd) {
                return """
                                { "variant_id": "%s", "quantidade": %d }
                                """.formatted(variantId, qtd);
        }

        private ResultActions adicionar(Cookie cookie, String variantId, int qtd) throws Exception {
                return mockMvc.perform(post("/api/v1/cart/items").cookie(cookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(itemBody(variantId, qtd)));
        }

        // ----------------- segurança -----------------

        @Test
        void getCart_semLogin_retorna401() throws Exception {
                mockMvc.perform(get("/api/v1/cart"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void carrinho_isoladoEntreUsuarios() throws Exception {
                Cookie clienteA = clienteCookie("a@test.com");
                Cookie clienteB = clienteCookie("b@test.com");
                CatalogoDeTeste.Item variant = catalogo.produto("Camisa", "100.00", 10);

                // A adiciona ao carrinho DELE
                String jsonA = mockMvc.perform(post("/api/v1/cart/items").cookie(clienteA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(itemBody(variant.variantId(), 1)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                String itemIdA = JsonPath.read(jsonA, "$.data.itens[0].itemId");

                // B vê o próprio carrinho -> vazio (não enxerga o de A)
                mockMvc.perform(get("/api/v1/cart").cookie(clienteB))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens.length()").value(0));

                // B tenta remover o item de A pelo id -> 404 (não está no carrinho de B)
                mockMvc.perform(delete("/api/v1/cart/items/" + itemIdA).cookie(clienteB))
                                .andExpect(status().isNotFound());
        }

        // ----------------- fluxo -----------------

        @Test
        void getCart_criaCarrinhoVazio() throws Exception {
                Cookie cli = clienteCookie("a@test.com");
                mockMvc.perform(get("/api/v1/cart").cookie(cli))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens.length()").value(0))
                                .andExpect(jsonPath("$.data.subtotal").value(0));
        }

        @Test
        void addItem_subtotalUsaPrecoDoCatalogo() throws Exception {
                Cookie cli = clienteCookie("a@test.com");
                var p = saveProduct("Camiseta", "100.00"); // preço definido no servidor

                mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                                .contentType(MediaType.APPLICATION_JSON).content(itemBody(p.variantId(), 2)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens.length()").value(1))
                                .andExpect(jsonPath("$.data.itens[0].quantidade").value(2))
                                .andExpect(jsonPath("$.data.subtotal").value(200.00)); // 2 * 100, calculado no servidor
        }

        @Test
        void addItem_produtoRepetido_somaQuantidade() throws Exception {
                Cookie cli = clienteCookie("a@test.com");
                var p = saveProduct("Camiseta", "50.00");

                mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                                .contentType(MediaType.APPLICATION_JSON).content(itemBody(p.variantId(), 2)));
                mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                                .contentType(MediaType.APPLICATION_JSON).content(itemBody(p.variantId(), 3)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens.length()").value(1)) // não duplica
                                .andExpect(jsonPath("$.data.itens[0].quantidade").value(5)); // 2 + 3
        }

        @Test
        void editItem_alteraQuantidade() throws Exception {
                Cookie cli = clienteCookie("a@test.com");
                var p = saveProduct("Camiseta", "50.00");
                mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                                .contentType(MediaType.APPLICATION_JSON).content(itemBody(p.variantId(), 2)));

                mockMvc.perform(patch("/api/v1/cart/items/edit").cookie(cli)
                                .contentType(MediaType.APPLICATION_JSON).content(itemBody(p.variantId(), 5)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens[0].quantidade").value(5)); // substitui, não soma
        }

        @Test
        void removeItem_removeDoCarrinho() throws Exception {
                Cookie cli = clienteCookie("a@test.com");
                var p = saveProduct("Camiseta", "50.00");
                String json = mockMvc.perform(post("/api/v1/cart/items").cookie(cli)
                                .contentType(MediaType.APPLICATION_JSON).content(itemBody(p.variantId(), 1)))
                                .andReturn().getResponse().getContentAsString();
                String itemId = JsonPath.read(json, "$.data.itens[0].itemId");

                mockMvc.perform(delete("/api/v1/cart/items/" + itemId).cookie(cli))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens.length()").value(0));
        }

        @Test
        void duasVariacoesDoMesmoProduto_viramDuasLinhas_comOPrecoDeCada() throws Exception {
                Cookie cli = clienteCookie("cli@test.com");
                var base = catalogo.produto("Camiseta Basica", "50.00", 10);
                var g = catalogo.variacao(base, "G", "Preto", "60.00");

                adicionar(cli, base.variantId(), 1).andExpect(status().isOk());
                adicionar(cli, g.getId(), 2)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.itens.length()").value(2))
                                .andExpect(jsonPath("$.data.subtotal").value(170.00)); // 50 + 2 x 60
        }

        @Test
        void variacaoDEsativada_resusadaNoCarrinho() throws Exception {
                var base = catalogo.produto("Camiseta Basica", "50.00", 10);
                var v = base.variacao();
                v.setAtivo(false);
                variantRepository.save(v);

                adicionar(clienteCookie("cli@test.com"), base.variantId(), 1)
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Esta variação do produto não está disponível"));
        }
}
