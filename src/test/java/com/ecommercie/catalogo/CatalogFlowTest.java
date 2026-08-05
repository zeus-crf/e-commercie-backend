package com.ecommercie.catalogo;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.catalogo.models.Category;
import com.ecommercie.catalogo.models.Product;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.catalogo.repository.CategoryRepository;
import com.ecommercie.catalogo.repository.ProductRepository;
import com.ecommercie.estoque.repository.InventoryItemRepository;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do catálogo (Fase 3).
 *
 * Foco: (1) o catálogo público é realmente público e só mostra ativos;
 *       (2) as escritas em /admin/** exigem ROLE_ADMIN (cliente -> 403, sem login -> 401);
 *       (3) caminho feliz do CRUD e os 404/400/guards.
 *
 * Mesmo setup do AuthFlowTest: Postgres real via Testcontainers, MockMvc com a
 * cadeia de filtros do Security aplicada. ADMIN é criado direto no banco (não há
 * endpoint público para isso); CLIENTE vem do /auth/register.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CatalogFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired InventoryItemRepository inventoryItemRepository;
    @Autowired CartRepository cartRepository;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // isola cada teste na ordem das FKs: carrinho -> estoque -> produtos -> categorias, depois usuarios
    @BeforeEach
    void limpar() {
        cartRepository.deleteAll();
        inventoryItemRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----------------- helpers -----------------

    private Category saveCategory(String nome, String slug) {
        return categoryRepository.save(Category.builder().nome(nome).slug(slug).build());
    }

    private Product saveProduct(Category category, String nome, boolean ativo) {
        return productRepository.save(Product.builder()
                .category(category)
                .nome(nome)
                .preco(new BigDecimal("99.90"))
                .ativo(ativo)
                .pesoKg(new BigDecimal("1.0"))
                .alturaCm(new BigDecimal("10.0"))
                .larguraCm(new BigDecimal("10.0"))
                .comprimentoCm(new BigDecimal("10.0"))
                .build());
    }

    private Cookie clienteCookie(String email) throws Exception {
        String body = """
                { "nome": "Cliente", "email": "%s", "senha": "senha123", "cpf_cnpj": "12345678900" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getCookie("access_token");
    }

    private Cookie adminCookie(String email) throws Exception {
        userRepository.save(User.builder()
                .nome("Chefe").email(email)
                .senha(passwordEncoder.encode("senha123"))
                .cpfCnpj("00000000000").papel(Papel.ADMIN).ativo(true)
                .build());
        String login = """
                { "email": "%s", "senha": "senha123" }
                """.formatted(email);
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andReturn().getResponse().getCookie("access_token");
    }

    private String productBody(String categoryId, String nome) {
        return """
                {
                  "category_id": "%s",
                  "nome": "%s",
                  "preco": 99.90,
                  "peso_kg": 1.0,
                  "altura_cm": 10.0,
                  "largura_cm": 10.0,
                  "comprimento_cm": 10.0
                }
                """.formatted(categoryId, nome);
    }

    // ----------------- catálogo público -----------------

    @Test
    void listarProdutos_semLogin_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listarProdutos_mostraSomenteAtivos() throws Exception {
        Category cat = saveCategory("Camisetas", "camisetas");
        saveProduct(cat, "Ativa", true);
        saveProduct(cat, "Inativa", false);

        mockMvc.perform(get("/api/v1/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].nome").value("Ativa"));
    }

    @Test
    void buscarProdutoInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/nao-existe"))
                .andExpect(status().isNotFound());
    }

    // ----------------- admin: segurança -----------------

    @Test
    void criarProduto_semLogin_retorna401() throws Exception {
        Category cat = saveCategory("Cat", "cat");
        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(cat.getId(), "Novo")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void criarProduto_comoCliente_retorna403() throws Exception {
        Category cat = saveCategory("Cat", "cat");
        Cookie cliente = clienteCookie("cli@test.com");

        mockMvc.perform(post("/api/v1/admin/catalog/products").cookie(cliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(cat.getId(), "Novo")))
                .andExpect(status().isForbidden());
    }

    // ----------------- admin: CRUD -----------------

    @Test
    void criarProduto_comoAdmin_retorna201EPersiste() throws Exception {
        Category cat = saveCategory("Cat", "cat");
        Cookie admin = adminCookie("admin@test.com");

        mockMvc.perform(post("/api/v1/admin/catalog/products").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody(cat.getId(), "Camiseta Nova")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nome").value("Camiseta Nova"))
                .andExpect(jsonPath("$.data.category_id").value(cat.getId()));

        assertThat(productRepository.existsByNomeIgnoreCase("Camiseta Nova")).isTrue();
    }

    @Test
    void criarProduto_semNome_retorna400() throws Exception {
        Category cat = saveCategory("Cat", "cat");
        Cookie admin = adminCookie("admin@test.com");
        String semNome = """
                { "category_id": "%s", "preco": 10.0, "peso_kg": 1.0,
                  "altura_cm": 10.0, "largura_cm": 10.0, "comprimento_cm": 10.0 }
                """.formatted(cat.getId());

        mockMvc.perform(post("/api/v1/admin/catalog/products").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(semNome))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletarProduto_comoAdmin_retorna204() throws Exception {
        Category cat = saveCategory("Cat", "cat");
        Product p = saveProduct(cat, "Produto", true);
        Cookie admin = adminCookie("admin@test.com");

        mockMvc.perform(delete("/api/v1/admin/catalog/products/" + p.getId()).cookie(admin))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findById(p.getId())).isEmpty();
    }

    @Test
    void deletarCategoria_comProdutos_retorna400() throws Exception {
        Category cat = saveCategory("Cat", "cat");
        saveProduct(cat, "Produto", true);
        Cookie admin = adminCookie("admin@test.com");

        mockMvc.perform(delete("/api/v1/admin/catalog/categories/" + cat.getId()).cookie(admin))
                .andExpect(status().isBadRequest());   // guard: categoria em uso não pode ser excluída
    }
}
