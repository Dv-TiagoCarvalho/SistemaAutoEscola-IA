package br.com.cfc.teorico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class TeoricoDtos {

    private TeoricoDtos() {
    }

    public record QuestaoRequest(
            @NotBlank @Size(max = 60) String tema,
            @NotBlank @Size(max = 1000) String enunciado,
            @NotBlank @Size(max = 400) String altA,
            @NotBlank @Size(max = 400) String altB,
            @NotBlank @Size(max = 400) String altC,
            @NotBlank @Size(max = 400) String altD,
            @NotBlank @Pattern(regexp = "[A-Da-d]", message = "A resposta correta deve ser A, B, C ou D") String correta,
            @Size(max = 600) String explicacao) {
    }

    public record QuestaoResponse(Long id, String tema, String enunciado, String altA, String altB, String altC,
                                  String altD, String correta, String explicacao, boolean ativa) {
        public static QuestaoResponse de(Questao q) {
            return new QuestaoResponse(q.getId(), q.getTema(), q.getEnunciado(), q.getAltA(), q.getAltB(), q.getAltC(),
                    q.getAltD(), q.getCorreta(), q.getExplicacao(), q.isAtiva());
        }
    }

    public record ImportacaoResponse(int importadas, List<String> erros) {
    }

    public record AulaTeoricaRequest(
            @NotBlank @Size(max = 120) String tema,
            Long instrutorId,
            @NotNull LocalDateTime inicio,
            @NotNull LocalDateTime fim,
            @Size(max = 120) String local) {
    }

    public record AulaTeoricaResponse(Long id, String tema, Long instrutorId, Instant inicio, Instant fim, String local,
                                      StatusAulaTeorica status) {
        public static AulaTeoricaResponse de(AulaTeorica a) {
            return new AulaTeoricaResponse(a.getId(), a.getTema(), a.getInstrutorId(), a.getInicio(), a.getFim(),
                    a.getLocal(), a.getStatus());
        }
    }

    public record PresencaRequest(@NotEmpty List<Item> itens) {
        public record Item(@NotNull Long alunoId, boolean presente) {
        }
    }

    public record PresencaResponse(Long alunoId, boolean presente) {
    }

    public record HorasTeoricasResponse(Long alunoId, BigDecimal horasAssistidas, BigDecimal horasMinimas) {
    }

    // ----- Simulado -----

    public record SimuladoQuestaoView(Long questaoId, int ordem, String enunciado, String altA, String altB,
                                      String altC, String altD, String resposta,
                                      /** Só preenchidos depois de finalizado. */
                                      String correta, Boolean acertou, String explicacao) {
    }

    public record SimuladoResponse(Long id, Long alunoId, Instant iniciadoEm, Instant limiteEm, Instant finalizadoEm,
                                   int qtdQuestoes, int acertosMinimos, Integer acertos, Boolean aprovado,
                                   boolean expirado, List<SimuladoQuestaoView> questoes) {
    }

    public record SimuladoResumo(Long id, Instant iniciadoEm, Instant finalizadoEm, int qtdQuestoes, Integer acertos,
                                 Boolean aprovado, boolean expirado) {
        public static SimuladoResumo de(Simulado s) {
            return new SimuladoResumo(s.getId(), s.getIniciadoEm(), s.getFinalizadoEm(), s.getQtdQuestoes(),
                    s.getAcertos(), s.getAprovado(), s.isExpirado());
        }
    }

    /** Mapa questaoId -> letra (A-D). Questões sem resposta contam como erro. */
    public record FinalizarSimuladoRequest(@NotNull Map<Long, String> respostas) {
    }

    public record DesempenhoResponse(long simuladosFeitos, long aprovados, List<SimuladoResumo> historico) {
    }
}
