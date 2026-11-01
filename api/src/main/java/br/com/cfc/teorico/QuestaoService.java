package br.com.cfc.teorico;

import br.com.cfc.shared.audit.AcaoAuditoria;
import br.com.cfc.shared.audit.AuditService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.teorico.TeoricoDtos.ImportacaoResponse;
import br.com.cfc.teorico.TeoricoDtos.QuestaoRequest;
import br.com.cfc.teorico.TeoricoDtos.QuestaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestaoService {

    private final QuestaoRepository repository;
    private final AuditService auditService;

    public QuestaoService(QuestaoRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<QuestaoResponse> listar(Boolean ativa, String tema) {
        SecurityUtils.autoescolaIdObrigatorio();
        String t = tema == null || tema.isBlank() ? null : tema.trim();
        return repository.buscar(ativa, t).stream().map(QuestaoResponse::de).toList();
    }

    @Transactional
    public QuestaoResponse criar(QuestaoRequest req) {
        SecurityUtils.autoescolaIdObrigatorio();
        Questao q = new Questao();
        preencher(q, req);
        q = repository.save(q);
        var r = QuestaoResponse.de(q);
        auditService.registrar("questao", q.getId(), AcaoAuditoria.CRIACAO, null, r);
        return r;
    }

    @Transactional
    public QuestaoResponse atualizar(Long id, QuestaoRequest req) {
        Questao q = exigir(id);
        var antes = QuestaoResponse.de(q);
        preencher(q, req);
        var depois = QuestaoResponse.de(q);
        auditService.registrar("questao", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    @Transactional
    public QuestaoResponse alterarStatus(Long id, boolean ativa) {
        Questao q = exigir(id);
        var antes = QuestaoResponse.de(q);
        q.setAtiva(ativa);
        var depois = QuestaoResponse.de(q);
        auditService.registrar("questao", id, AcaoAuditoria.ALTERACAO, antes, depois);
        return depois;
    }

    /**
     * Importa CSV separado por ponto e vírgula: tema;enunciado;A;B;C;D;correta;explicacao.
     * Linhas inválidas são relatadas e as demais são importadas.
     */
    @Transactional
    public ImportacaoResponse importarCsv(String conteudo) {
        SecurityUtils.autoescolaIdObrigatorio();
        List<String> erros = new ArrayList<>();
        int importadas = 0;
        String[] linhas = conteudo.replace("﻿", "").split("\\R");
        for (int i = 0; i < linhas.length; i++) {
            String linha = linhas[i].trim();
            if (linha.isEmpty() || (i == 0 && linha.toLowerCase().startsWith("tema;"))) {
                continue;
            }
            String[] c = linha.split(";", -1);
            if (c.length < 7) {
                erros.add("Linha " + (i + 1) + ": esperado ao menos 7 colunas.");
                continue;
            }
            String correta = c[6].trim().toUpperCase();
            if (!correta.matches("[A-D]") || vazio(c, 0, 5)) {
                erros.add("Linha " + (i + 1) + ": campos obrigatórios vazios ou resposta correta inválida.");
                continue;
            }
            Questao q = new Questao();
            q.setTema(c[0].trim());
            q.setEnunciado(c[1].trim());
            q.setAltA(c[2].trim());
            q.setAltB(c[3].trim());
            q.setAltC(c[4].trim());
            q.setAltD(c[5].trim());
            q.setCorreta(correta);
            q.setExplicacao(c.length > 7 && !c[7].isBlank() ? c[7].trim() : null);
            repository.save(q);
            importadas++;
        }
        if (importadas > 0) {
            auditService.registrar("questao", 0L, AcaoAuditoria.CRIACAO, null, "Importação de " + importadas + " questões");
        }
        return new ImportacaoResponse(importadas, erros);
    }

    private static boolean vazio(String[] c, int de, int ate) {
        for (int i = de; i <= ate; i++) {
            if (c[i].isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void preencher(Questao q, QuestaoRequest r) {
        q.setTema(r.tema().trim());
        q.setEnunciado(r.enunciado().trim());
        q.setAltA(r.altA().trim());
        q.setAltB(r.altB().trim());
        q.setAltC(r.altC().trim());
        q.setAltD(r.altD().trim());
        q.setCorreta(r.correta().trim().toUpperCase());
        q.setExplicacao(r.explicacao() == null || r.explicacao().isBlank() ? null : r.explicacao().trim());
    }

    private Questao exigir(Long id) {
        return Tenants.daAutoescolaAtual(repository.findById(id), "Questão não encontrada.");
    }
}
