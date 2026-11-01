package br.com.cfc.teorico;

import br.com.cfc.aluno.Aluno;
import br.com.cfc.aluno.AlunoRepository;
import br.com.cfc.aluno.AlunoService;
import br.com.cfc.autoescola.ConfiguracaoAutoescola;
import br.com.cfc.autoescola.ConfiguracaoService;
import br.com.cfc.shared.domain.Tenants;
import br.com.cfc.shared.exception.NaoEncontradoException;
import br.com.cfc.shared.exception.RegraNegocioException;
import br.com.cfc.shared.security.Perfil;
import br.com.cfc.shared.security.SecurityUtils;
import br.com.cfc.shared.security.UsuarioAutenticado;
import br.com.cfc.teorico.TeoricoDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Simulados do aluno. O gabarito só sai para o cliente depois de finalizado, e o tempo é controlado no servidor:
 * respostas enviadas depois do limite (com 30 s de tolerância de rede) reprovam o simulado como expirado.
 */
@Service
public class SimuladoService {

    static final Duration TOLERANCIA = Duration.ofSeconds(30);

    private final SimuladoRepository repository;
    private final SimuladoQuestaoRepository itemRepository;
    private final QuestaoRepository questaoRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoService alunoService;
    private final ConfiguracaoService configuracaoService;
    private final Clock clock;

    public SimuladoService(SimuladoRepository repository, SimuladoQuestaoRepository itemRepository,
                           QuestaoRepository questaoRepository, AlunoRepository alunoRepository,
                           AlunoService alunoService, ConfiguracaoService configuracaoService, Clock clock) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.questaoRepository = questaoRepository;
        this.alunoRepository = alunoRepository;
        this.alunoService = alunoService;
        this.configuracaoService = configuracaoService;
        this.clock = clock;
    }

    /** Inicia um simulado ou retoma o que já está em andamento. */
    @Transactional
    public SimuladoResponse iniciar() {
        Aluno aluno = alunoLogado();
        Instant agora = clock.instant();
        for (Simulado aberto : repository.findTop50ByAlunoIdOrderByIniciadoEmDescIdDesc(aluno.getId())) {
            if (aberto.getFinalizadoEm() == null) {
                if (agora.isBefore(aberto.getLimiteEm())) {
                    return montar(aberto, false);
                }
                corrigir(aberto, Map.of(), agora, true);
            }
        }
        ConfiguracaoAutoescola cfg = configuracaoService.configuracaoAtual();
        List<Questao> ativas = new ArrayList<>(questaoRepository.findByAtivaTrue());
        if (ativas.size() < cfg.getSimuladoQtdQuestoes()) {
            throw new RegraNegocioException("O banco de questões tem " + ativas.size()
                    + " questões ativas e o simulado exige " + cfg.getSimuladoQtdQuestoes() + ".");
        }
        Collections.shuffle(ativas);
        Simulado s = new Simulado();
        s.setAlunoId(aluno.getId());
        s.setIniciadoEm(agora);
        s.setLimiteEm(agora.plus(Duration.ofMinutes(cfg.getSimuladoTempoMin())));
        s.setQtdQuestoes(cfg.getSimuladoQtdQuestoes());
        s.setAcertosMinimos(Math.min(cfg.getSimuladoAcertosMinimos(), cfg.getSimuladoQtdQuestoes()));
        s = repository.save(s);
        int ordem = 1;
        for (Questao q : ativas.subList(0, cfg.getSimuladoQtdQuestoes())) {
            SimuladoQuestao item = new SimuladoQuestao();
            item.setSimuladoId(s.getId());
            item.setQuestaoId(q.getId());
            item.setOrdem(ordem++);
            itemRepository.save(item);
        }
        return montar(s, false);
    }

    @Transactional
    public SimuladoResponse finalizar(Long id, FinalizarSimuladoRequest req) {
        Aluno aluno = alunoLogado();
        Simulado s = repository.buscarComLock(id)
                .filter(x -> x.getAlunoId().equals(aluno.getId()))
                .orElseThrow(() -> new NaoEncontradoException("Simulado não encontrado."));
        Tenants.daAutoescolaAtual(java.util.Optional.of(s), "Simulado não encontrado.");
        if (s.getFinalizadoEm() != null) {
            throw new RegraNegocioException("Este simulado já foi finalizado.");
        }
        Instant agora = clock.instant();
        boolean expirado = agora.isAfter(s.getLimiteEm().plus(TOLERANCIA));
        corrigir(s, req.respostas(), agora, expirado);
        return montar(s, true);
    }

    @Transactional(readOnly = true)
    public SimuladoResponse buscar(Long id) {
        Simulado s = Tenants.daAutoescolaAtual(repository.findById(id), "Simulado não encontrado.");
        UsuarioAutenticado u = SecurityUtils.usuarioObrigatorio();
        if (u.perfil() == Perfil.ALUNO && !alunoLogado().getId().equals(s.getAlunoId())) {
            throw new NaoEncontradoException("Simulado não encontrado.");
        }
        boolean revelar = s.getFinalizadoEm() != null;
        return montar(s, revelar);
    }

    @Transactional(readOnly = true)
    public DesempenhoResponse meuDesempenho() {
        return desempenho(alunoLogado().getId());
    }

    @Transactional(readOnly = true)
    public DesempenhoResponse desempenhoDoAluno(Long alunoId) {
        alunoService.exigir(alunoId);
        return desempenho(alunoId);
    }

    private DesempenhoResponse desempenho(Long alunoId) {
        var historico = repository.findTop50ByAlunoIdOrderByIniciadoEmDescIdDesc(alunoId).stream()
                .map(SimuladoResumo::de).toList();
        return new DesempenhoResponse(repository.countByAlunoIdAndFinalizadoEmIsNotNull(alunoId),
                repository.countByAlunoIdAndAprovadoTrue(alunoId), historico);
    }

    private void corrigir(Simulado s, Map<Long, String> respostas, Instant agora, boolean expirado) {
        List<SimuladoQuestao> itens = itemRepository.findBySimuladoIdOrderByOrdem(s.getId());
        Map<Long, Questao> questoes = questaoRepository.findByIdIn(itens.stream().map(SimuladoQuestao::getQuestaoId).toList())
                .stream().collect(Collectors.toMap(Questao::getId, Function.identity()));
        int acertos = 0;
        for (SimuladoQuestao item : itens) {
            String r = respostas.get(item.getQuestaoId());
            String letra = r == null || r.isBlank() ? null : r.trim().toUpperCase();
            if (letra != null && !letra.matches("[A-D]")) {
                throw new RegraNegocioException("Resposta inválida: use A, B, C ou D.");
            }
            boolean certa = letra != null && letra.equals(questoes.get(item.getQuestaoId()).getCorreta());
            item.setResposta(letra);
            item.setCorreta(certa);
            if (certa) {
                acertos++;
            }
        }
        s.setFinalizadoEm(agora);
        s.setAcertos(acertos);
        s.setExpirado(expirado);
        s.setAprovado(!expirado && acertos >= s.getAcertosMinimos());
    }

    private SimuladoResponse montar(Simulado s, boolean revelar) {
        List<SimuladoQuestao> itens = itemRepository.findBySimuladoIdOrderByOrdem(s.getId());
        Map<Long, Questao> questoes = questaoRepository.findByIdIn(itens.stream().map(SimuladoQuestao::getQuestaoId).toList())
                .stream().collect(Collectors.toMap(Questao::getId, Function.identity()));
        List<SimuladoQuestaoView> views = itens.stream().map(i -> {
            Questao q = questoes.get(i.getQuestaoId());
            return new SimuladoQuestaoView(q.getId(), i.getOrdem(), q.getEnunciado(), q.getAltA(), q.getAltB(), q.getAltC(),
                    q.getAltD(), i.getResposta(), revelar ? q.getCorreta() : null, revelar ? i.getCorreta() : null,
                    revelar ? q.getExplicacao() : null);
        }).toList();
        return new SimuladoResponse(s.getId(), s.getAlunoId(), s.getIniciadoEm(), s.getLimiteEm(), s.getFinalizadoEm(),
                s.getQtdQuestoes(), s.getAcertosMinimos(), s.getAcertos(), s.getAprovado(), s.isExpirado(), views);
    }

    private Aluno alunoLogado() {
        UsuarioAutenticado u = SecurityUtils.usuarioObrigatorio();
        if (u.perfil() != Perfil.ALUNO) {
            throw new RegraNegocioException("Apenas alunos realizam simulados.");
        }
        return alunoRepository.findByUsuarioId(u.id())
                .orElseThrow(() -> new RegraNegocioException("Seu usuário não está vinculado a um cadastro de aluno."));
    }
}
