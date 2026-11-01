package br.com.cfc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sobe MySQL real (Docker), roda as migrations e o seed, e confere o isolamento entre autoescolas. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "app.bootstrap.seed-exemplo=true",
        "app.bootstrap.superadmin-email=super@cfc.local",
        "app.bootstrap.superadmin-senha=Senha@123",
        "app.security.login.max-tentativas-por-minuto-ip=1000"})
class TenantIsolationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"Senha@123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    @Test
    void cadaAutoescolaVeSoSeusDados() throws Exception {
        String exemplo = token("admin@cfcexemplo.com.br");
        String modelo = token("admin@cfcmodelo.com.br");

        // Exemplo altera a regra da categoria B
        mvc.perform(put("/api/configuracoes/regras-categoria/B").header("Authorization", exemplo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasPraticasMinimas\":25.0,\"horasTeoricasMinimas\":45.0,\"idadeMinima\":18,\"ativa\":true}"))
                .andExpect(status().isOk());

        // Modelo continua vendo o valor padrão (2.0) na própria regra B
        JsonNode regrasModelo = json.readTree(mvc.perform(get("/api/configuracoes/regras-categoria").header("Authorization", modelo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(regrasModelo).hasSize(7);
        regrasModelo.forEach(r -> {
            if (r.get("categoria").asText().equals("B")) {
                assertThat(r.get("horasPraticasMinimas").decimalValue()).isEqualByComparingTo("2.0");
            }
        });

        // Equipe: Exemplo tem 3 usuários, Modelo tem 1
        JsonNode equipeModelo = json.readTree(mvc.perform(get("/api/usuarios").header("Authorization", modelo))
                .andReturn().getResponse().getContentAsString());
        assertThat(equipeModelo.get("totalItens").asLong()).isEqualTo(1);
    }

    @Test
    void permissoesPorPerfil() throws Exception {
        String secretaria = token("secretaria@cfcexemplo.com.br");
        mvc.perform(get("/api/usuarios").header("Authorization", secretaria)).andExpect(status().isForbidden());
        mvc.perform(get("/api/plataforma/autoescolas").header("Authorization", secretaria)).andExpect(status().isForbidden());
        mvc.perform(get("/api/configuracoes").header("Authorization", secretaria)).andExpect(status().isOk());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());

        String sup = token("super@cfc.local");
        JsonNode lista = json.readTree(mvc.perform(get("/api/plataforma/autoescolas").header("Authorization", sup))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(lista.get("totalItens").asLong()).isEqualTo(2);
    }

    @Test
    void refreshERotacao() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"instrutor@cfcexemplo.com.br\",\"senha\":\"Senha@123\"}")).andReturn().getResponse().getContentAsString();
        String refresh = json.readTree(body).get("refreshToken").asText();
        String req = "{\"refreshToken\":\"" + refresh + "\"}";
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(req)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(req)).andExpect(status().isUnauthorized());
    }
}
