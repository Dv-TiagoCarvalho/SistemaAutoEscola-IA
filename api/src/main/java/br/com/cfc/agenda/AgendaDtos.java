package br.com.cfc.agenda;

import br.com.cfc.autoescola.Categoria;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class AgendaDtos {

    private AgendaDtos() {
    }

    // ---------- Instrutores ----------

    public record InstrutorRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome com no máximo 120 caracteres.") String nome,
            @Size(max = 20, message = "Telefone inválido.") String telefone,
            LocalDate cnhValidade,
            @NotEmpty(message = "Informe ao menos uma categoria.") Set<Categoria> categorias,
            @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            String senha) {
    }

    public record InstrutorResponse(Long id, String nome, String telefone, LocalDate cnhValidade,
                                    List<Categoria> categorias, boolean ativo, boolean temAcesso) {
        public static InstrutorResponse de(Instrutor i) {
            return new InstrutorResponse(i.getId(), i.getNome(), i.getTelefone(), i.getCnhValidade(),
                    i.categoriasSet().stream().sorted().toList(), i.isAtivo(), i.getUsuarioId() != null);
        }
    }

    public record AtivoRequest(@NotNull(message = "Informe se está ativo.") Boolean ativo) {
    }

    // ---------- Veículos ----------

    public record VeiculoRequest(
            @NotBlank(message = "Informe a placa.") @Pattern(regexp = "^[A-Za-z]{3}-?[0-9][A-Za-z0-9][0-9]{2}$", message = "Placa inválida.") String placa,
            @NotBlank(message = "Informe o modelo.") @Size(max = 80, message = "Modelo com no máximo 80 caracteres.") String modelo,
            @NotNull(message = "Informe o ano.") @Min(value = 1980, message = "Ano inválido.") @Max(value = 2100, message = "Ano inválido.") Integer ano,
            @NotNull(message = "Informe a categoria.") Categoria categoria) {
    }

    public record VeiculoResponse(Long id, String placa, String modelo, int ano, Categoria categoria, boolean ativo) {
        public static VeiculoResponse de(Veiculo v) {
            return new VeiculoResponse(v.getId(), v.getPlaca(), v.getModelo(), v.getAno(), v.getCategoria(), v.isAtivo());
        }
    }

    // ---------- Aulas ----------

    /** [inicio] é o horário local da autoescola (sem fuso), ex.: 2026-11-05T14:00. */
    public record AulaRequest(
            @NotNull(message = "Informe a matrícula.") Long matriculaId,
            @NotNull(message = "Informe o instrutor.") Long instrutorId,
            @NotNull(message = "Informe o veículo.") Long veiculoId,
            @NotNull(message = "Informe o início da aula.") LocalDateTime inicio) {
    }

    public record CancelarAulaRequest(
            @NotBlank(message = "Informe o motivo.") @Size(max = 300, message = "Motivo com no máximo 300 caracteres.") String motivo,
            /** Só a equipe pode isentar a cobrança de cancelamento tardio. */
            Boolean isentarCobranca) {
    }

    public record RegistroAulaRequest(
            @NotNull(message = "Informe se a aula foi realizada ou se o aluno faltou.") StatusAula resultado,
            @Min(value = 0, message = "Km inválido.") @Max(value = 9999999, message = "Km inválido.") Integer kmInicial,
            @Min(value = 0, message = "Km inválido.") @Max(value = 9999999, message = "Km inválido.") Integer kmFinal,
            @Size(max = 300, message = "Conteúdo com no máximo 300 caracteres.") String conteudo,
            @Size(max = 500, message = "Observações com no máximo 500 caracteres.") String observacoes,
            @Min(value = 1, message = "A nota vai de 1 a 5.") @Max(value = 5, message = "A nota vai de 1 a 5.") Integer nota) {
    }

    public record AulaResponse(Long id, Long alunoId, String alunoNome, Long matriculaId, Long instrutorId,
                               String instrutorNome, Long veiculoId, String veiculoPlaca, Instant inicio, Instant fim,
                               StatusAula status, String motivoCancelamento, boolean cancelamentoTardio,
                               Integer kmInicial, Integer kmFinal, String conteudo, String observacoes, Integer nota) {
        public static AulaResponse de(Aula a, String alunoNome, String instrutorNome, String placa) {
            return new AulaResponse(a.getId(), a.getAlunoId(), alunoNome, a.getMatriculaId(), a.getInstrutorId(),
                    instrutorNome, a.getVeiculoId(), placa, a.getInicio(), a.getFim(), a.getStatus(),
                    a.getMotivoCancelamento(), a.isCancelamentoTardio(), a.getKmInicial(), a.getKmFinal(),
                    a.getConteudo(), a.getObservacoes(), a.getNota());
        }
    }

    public record SaldoAulasResponse(Long matriculaId, int contratadas, int consumidas, int saldo) {
    }
}
