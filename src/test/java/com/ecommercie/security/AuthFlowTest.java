package com.ecommercie.security;

import com.ecommercie.TestcontainersConfiguration;
import com.ecommercie.carrinho.repository.CartRepository;
import com.ecommercie.security.models.Papel;
import com.ecommercie.security.models.User;
import com.ecommercie.security.repository.RefreshTokenRepository;
import com.ecommercie.security.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração do fluxo de autenticação (Fase 2).
 *
 * @SpringBootTest        -> sobe o contexto inteiro da aplicação.
 * @AutoConfigureMockMvc  -> cria o MockMvc já com a cadeia de filtros do Spring Security
 *                           aplicada (então o nosso JwtAuthFilter e as regras de papel valem).
 * @Import(Testcontainers)-> Postgres real em container (mesmo padrão do resto).
 *
 * O MockMvc simula requisições HTTP contra a camada web SEM subir um servidor Tomcat:
 * a requisição entra pela cadeia de filtros -> controller -> volta a resposta, tudo em memória.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired CartRepository cartRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // isola cada teste: carrinhos e refresh_tokens (FKs) antes de usuarios
    @BeforeEach
    void limpar() {
        cartRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ----------------- helpers -----------------

    private String registerBody(String email, String senha) {
        return """
                { "nome": "Fulano", "email": "%s", "senha": "%s", "cpf_cnpj": "12345678900" }
                """.formatted(email, senha);
    }

    private String loginBody(String email, String senha) {
        return """
                { "email": "%s", "senha": "%s" }
                """.formatted(email, senha);
    }

    private MvcResult register(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, senha)))
                .andReturn();
    }

    // ----------------- testes -----------------

    @Test
    void register_criaClienteComSenhaCriptografada_eSetaCookies() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("fulano@test.com", "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("fulano@test.com"))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("access_token", true));

        User salvo = userRepository.findByEmail("fulano@test.com").orElseThrow();
        assertThat(salvo.getPapel()).isEqualTo(Papel.CLIENTE);
        assertThat(salvo.getSenha()).isNotEqualTo("senha123");                       // não guardou crua
        assertThat(passwordEncoder.matches("senha123", salvo.getSenha())).isTrue();  // BCrypt confere
    }

    @Test
    void register_emailDuplicado_retorna400() throws Exception {
        register("dup@test.com", "senha123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("dup@test.com", "outrasenha")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_comSenhaCorreta_retorna200ESetaCookies() throws Exception {
        register("login@test.com", "senha123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("login@test.com", "senha123")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void login_comSenhaErrada_retorna401() throws Exception {
        register("erro@test.com", "senhaCerta");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("erro@test.com", "senhaErrada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_semCookie_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_comCookieDeAccess_retorna200EDadosDoUsuario() throws Exception {
        Cookie access = register("me@test.com", "senha123").getResponse().getCookie("access_token");

        mockMvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@test.com"));
    }

    @Test
    void adminEndpoint_semAuth_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/qualquer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_clienteRecebe403() throws Exception {
        Cookie access = register("cli@test.com", "senha123").getResponse().getCookie("access_token");

        mockMvc.perform(get("/api/v1/admin/qualquer").cookie(access))
                .andExpect(status().isForbidden());   // CLIENTE barrado no /admin
    }

    @Test
    void adminEndpoint_adminPassaNaSeguranca() throws Exception {
        // não há endpoint público para criar ADMIN, então criamos direto no banco
        userRepository.save(User.builder()
                .nome("Chefe").email("admin@test.com")
                .senha(passwordEncoder.encode("senha123"))
                .cpfCnpj("00000000000").papel(Papel.ADMIN).ativo(true)
                .build());

        Cookie access = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@test.com", "senha123")))
                .andReturn().getResponse().getCookie("access_token");

        // ainda não existe controller em /admin, então dá 404 —
        // o que importa é que NÃO deu 403: a segurança deixou o ADMIN passar.
        mockMvc.perform(get("/api/v1/admin/qualquer").cookie(access))
                .andExpect(status().isNotFound());
    }
}
