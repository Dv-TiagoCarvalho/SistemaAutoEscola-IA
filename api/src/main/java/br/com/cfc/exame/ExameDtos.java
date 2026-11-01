package br.com.cfc.exame;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;

public final class ExameDtos {

    private ExameDtos() {
    }

    /** [dataHora] é o horário local da autoescola (sem fuso). */
    public record ExameRequest(
            @NotNull(message = "Informe o processo do aluno.") Long processoId,
            @NotNull(message = "Informe o tipo do exame.") TipoExame tipo,
            @NotNull(message = "Informe a data e a hora.") LocalDateTime dataHora,
            @Size(max = 120, message = "Local com no máximo 120 caracteres.") String local) {
    }

    public record ResultadoRequest(
            @NotNull(message = "Informe o resultado.") ResultadoExame resultado,
            @Size(max = 300, message = "Observações com no máximo 300 caracteres.") String observacoes) {
    }

    public record ExameResponse(Long id, Long alunoId, String alunoNome, Long processoId, TipoExame tipo, Instant dataHora,
                                String local, int tentativa, ResultadoExame resultado, String observacoes) {
        public static ExameResponse de(Exame e, String alunoNome) {
            return new ExameResponse(e.getId(), e.getAlunoId(), alunoNome, e.getProcessoId(), e.getTipo(), e.getDataHora(),
                    e.getLocal(), e.getTentativa(), e.getResultado(), e.getObservacoes());
        }
    }
}
