package com.ecommercie.config;

import com.ecommercie.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec OpenAPI (Fase 12): publica sem login, declara a auth por cookie
 * e nenhum endpoint fica sem summary.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode spec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    void declaraAuthPorCookieHttpOnly() throws Exception {
        JsonNode scheme = spec().at("/components/securitySchemes/" + OpenApiConfig.COOKIE_AUTH);

        assertThat(scheme.path("type").asText()).isEqualTo("apiKey");
        assertThat(scheme.path("in").asText()).isEqualTo("cookie");
        assertThat(scheme.path("name").asText()).isEqualTo("access_token");
    }

    @Test
    void endpointAutenticadoTemCadeadoEPublicoNao() throws Exception {
        JsonNode paths = spec().path("paths");

        assertThat(paths.at("/~1api~1v1~1cart/get/security").toString()).contains(OpenApiConfig.COOKIE_AUTH);
        assertThat(paths.at("/~1api~1v1~1auth~1me/get/security").toString()).contains(OpenApiConfig.COOKIE_AUTH);
        assertThat(paths.at("/~1api~1v1~1catalog~1products/get/security").isMissingNode()).isTrue();
        assertThat(paths.at("/~1api~1v1~1auth~1login/post/security").isMissingNode()).isTrue();
    }

    @Test
    void todoEndpointTemSummary() throws Exception {
        List<String> semSummary = new ArrayList<>();
        spec().path("paths").properties().forEach(path ->
                path.getValue().properties().forEach(op -> {
                    if (HTTP_METHODS.contains(op.getKey()) && op.getValue().path("summary").asText().isBlank()) {
                        semSummary.add(op.getKey().toUpperCase() + " " + path.getKey());
                    }
                }));

        assertThat(semSummary).isEmpty();
    }
}
