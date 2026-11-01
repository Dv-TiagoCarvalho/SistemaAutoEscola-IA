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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    private String criar(String url, String token, String corpo) throws Exception {
        String r = mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).path("id").asText(json.readTree(r).path("matricula").path("id").asText());
    }

    @Test
    void fluxoAlunoMatriculaContratoEIsolamentoEntreAutoescolas() throws Exception {
        String exemplo = token("admin@cfcexemplo.com.br");
        String modelo = token("admin@cfcmodelo.com.br");

        String alunoId = criar("/api/alunos", exemplo, """
                {"nome":"Maria Silva","cpf":"529.982.247-25","dataNascimento":"2000-01-15","telefone":"(31) 99999-0000","email":"maria@x.com"}""");
        // mesmo CPF na mesma autoescola: conflito; em outra autoescola: permitido
        mvc.perform(post("/api/alunos").header("Authorization", exemplo).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome":"Outra","cpf":"52998224725","dataNascimento":"2000-01-15","telefone":"31999990000"}""")).andExpect(status().isConflict());
        criar("/api/alunos", modelo, """
                {"nome":"Maria na Modelo","cpf":"52998224725","dataNascimento":"2000-01-15","telefone":"11999990000"}""");

        String processoId = criar("/api/alunos/" + alunoId + "/processos", exemplo,
                "{\"categoria\":\"B\",\"tipo\":\"PRIMEIRA_HABILITACAO\",\"renach\":\"MG123456789\"}");
        String pacoteId = criar("/api/pacotes", exemplo,
                "{\"categoria\":\"B\",\"nome\":\"Pacote B\",\"qtdAulasPraticas\":20,\"teoricoIncluso\":true,\"preco\":1200.00}");

        String venc = LocalDate.now().plusDays(10).toString();
        String matriculaId = criar("/api/matriculas", exemplo, "{\"processoId\":" + processoId + ",\"pacoteId\":" + pacoteId
                + ",\"desconto\":100.00,\"entrada\":200.00,\"numParcelas\":3,\"primeiroVencimento\":\"" + venc + "\"}");

        JsonNode detalhe = json.readTree(mvc.perform(get("/api/matriculas/" + matriculaId).header("Authorization", exemplo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detalhe.get("titulos")).hasSize(4);
        assertThat(detalhe.get("aulasContratadas").asInt()).isEqualTo(20);
        assertThat(detalhe.get("matricula").get("valorTotal").decimalValue()).isEqualByComparingTo("1100.00");

        byte[] pdf = mvc.perform(get("/api/matriculas/" + matriculaId + "/contrato").header("Authorization", exemplo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // upload de documento: PNG válido entra, texto disfarçado de imagem não
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        mvc.perform(multipart("/api/alunos/" + alunoId + "/documentos").file(new org.springframework.mock.web.MockMultipartFile("arquivo", "rg.png", "image/png", png))
                .param("tipo", "RG").header("Authorization", exemplo)).andExpect(status().isCreated());
        mvc.perform(multipart("/api/alunos/" + alunoId + "/documentos").file(new org.springframework.mock.web.MockMultipartFile("arquivo", "x.png", "image/png", "oi".getBytes()))
                .param("tipo", "RG").header("Authorization", exemplo)).andExpect(status().isUnprocessableEntity());

        // A outra autoescola não enxerga nada disso: tudo responde 404
        for (String url : new String[]{"/api/alunos/" + alunoId, "/api/alunos/" + alunoId + "/processos",
                "/api/alunos/" + alunoId + "/documentos", "/api/matriculas/" + matriculaId,
                "/api/matriculas/" + matriculaId + "/contrato"}) {
            mvc.perform(get(url).header("Authorization", modelo)).andExpect(status().isNotFound());
        }
        JsonNode lista = json.readTree(mvc.perform(get("/api/alunos").header("Authorization", modelo))
                .andReturn().getResponse().getContentAsString());
        assertThat(lista.get("totalItens").asLong()).isEqualTo(1);
        mvc.perform(post("/api/matriculas").header("Authorization", modelo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"processoId\":" + processoId + ",\"pacoteId\":" + pacoteId + ",\"numParcelas\":1}"))
                .andExpect(status().isNotFound());

        // ---- Agenda (Fase 3) ----
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/processos/" + processoId)
                .header("Authorization", exemplo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"renach\":\"MG123456789\",\"exameMedico\":\"APTO\",\"examePsicologico\":\"APTO\",\"situacao\":\"MATRICULADO\"}"))
                .andExpect(status().isOk());
        String instrutorId = criar("/api/instrutores", exemplo, "{\"nome\":\"Carlos\",\"categorias\":[\"B\"]}");
        String veiculoId = criar("/api/veiculos", exemplo, "{\"placa\":\"abc-1d23\",\"modelo\":\"Onix\",\"ano\":2022,\"categoria\":\"B\"}");
        String quando = LocalDate.now().plusDays(3) + "T10:00";
        String corpoAula = "{\"matriculaId\":" + matriculaId + ",\"instrutorId\":" + instrutorId + ",\"veiculoId\":" + veiculoId + ",\"inicio\":\"" + quando + "\"}";
        String aulaId = criar("/api/aulas", exemplo, corpoAula);
        // mesmo horário: conflito
        mvc.perform(post("/api/aulas").header("Authorization", exemplo).contentType(MediaType.APPLICATION_JSON).content(corpoAula))
                .andExpect(status().isConflict());
        JsonNode saldo = json.readTree(mvc.perform(get("/api/aulas/saldo?matriculaId=" + matriculaId).header("Authorization", exemplo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(saldo.get("saldo").asInt()).isEqualTo(19);
        JsonNode agenda = json.readTree(mvc.perform(get("/api/aulas?de=" + LocalDate.now().plusDays(3) + "&ate=" + LocalDate.now().plusDays(3))
                .header("Authorization", exemplo)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(agenda).hasSize(1);
        // outra autoescola não vê nem cancela
        JsonNode agendaModelo = json.readTree(mvc.perform(get("/api/aulas?de=" + LocalDate.now().plusDays(3) + "&ate=" + LocalDate.now().plusDays(3))
                .header("Authorization", modelo)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(agendaModelo).isEmpty();
        mvc.perform(post("/api/aulas/" + aulaId + "/cancelamento").header("Authorization", modelo)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/instrutores").header("Authorization", modelo)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());
        mvc.perform(post("/api/aulas/" + aulaId + "/cancelamento").header("Authorization", exemplo)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Chuva\"}")).andExpect(status().isOk());

        // Instrutor não acessa alunos
        mvc.perform(get("/api/alunos").header("Authorization", token("instrutor@cfcexemplo.com.br"))).andExpect(status().isForbidden());
    }
}
