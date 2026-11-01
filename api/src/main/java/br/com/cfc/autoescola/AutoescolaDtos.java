package br.com.cfc.autoescola;

import br.com.cfc.shared.validation.Cnpj;
import br.com.cfc.usuario.SenhaPolitica;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class AutoescolaDtos {

    public static final String REGEX_UF =
            "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO";

    private AutoescolaDtos() {
    }

    // ---------- Autoescola (SUPER_ADMIN) ----------

    public record AdminInicialRequest(
            @NotBlank(message = "Informe o nome do administrador.") @Size(max = 120) String nome,
            @NotBlank(message = "Informe o e-mail do administrador.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotBlank(message = "Informe a senha inicial.") @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM) String senha) {
    }

    public record CriarAutoescolaRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
            @NotBlank(message = "Informe o CNPJ.") @Cnpj String cnpj,
            @NotBlank(message = "Informe a UF.") @Pattern(regexp = REGEX_UF, message = "UF inválida.") String uf,
            @NotBlank(message = "Informe a cidade.") @Size(max = 100) String cidade,
            @Size(max = 20) String telefone,
            @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotNull(message = "Informe o administrador inicial.") @Valid AdminInicialRequest admin) {
    }

    public record AtualizarAutoescolaRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
            @NotBlank(message = "Informe o CNPJ.") @Cnpj String cnpj,
            @NotBlank(message = "Informe a UF.") @Pattern(regexp = REGEX_UF, message = "UF inválida.") String uf,
            @NotBlank(message = "Informe a cidade.") @Size(max = 100) String cidade,
            @Size(max = 20) String telefone,
            @Email(message = "E-mail inválido.") @Size(max = 160) String email) {
    }

    public record StatusRequest(@NotNull(message = "Informe se a autoescola está ativa.") Boolean ativo) {
    }

    public record AutoescolaResponse(Long id, String nome, String cnpj, String uf, String cidade, String telefone,
                                     String email, boolean ativo, Instant criadoEm) {
        public static AutoescolaResponse de(Autoescola a) {
            return new AutoescolaResponse(a.getId(), a.getNome(), a.getCnpj(), a.getUf(), a.getCidade(),
                    a.getTelefone(), a.getEmail(), a.isAtivo(), a.getCriadoEm());
        }
    }

    // ---------- Configurações (ADMIN) ----------

    public record ConfiguracaoRequest(
            @NotNull @Min(value = 20, message = "Duração mínima de 20 minutos.") @Max(value = 180, message = "Duração máxima de 180 minutos.") Integer duracaoAulaMin,
            @NotNull @Min(1) @Max(10) Integer maxAulasDiaAluno,
            @NotNull @Min(0) @Max(168) Integer antecedenciaCancelamentoHoras,
            @NotNull @Min(0) @Max(168) Integer antecedenciaAgendamentoHoras,
            @NotNull Boolean cobrarFalta,
            @NotNull @DecimalMin(value = "0.00", message = "Valor não pode ser negativo.") @Digits(integer = 10, fraction = 2) BigDecimal valorFalta,
            @NotNull @Min(1) @Max(72) Integer lembreteHorasAntes,
            @NotNull @Min(5) @Max(100) Integer simuladoQtdQuestoes,
            @NotNull @Min(5) @Max(240) Integer simuladoTempoMin,
            @NotNull @Min(1) Integer simuladoAcertosMinimos,
            @NotBlank @Size(max = 40) String fusoHorario,
            /** Versão lida pelo app; impede sobrescrever uma alteração feita por outra pessoa. */
            @NotNull Long version) {
    }

    public record ConfiguracaoResponse(Integer duracaoAulaMin, Integer maxAulasDiaAluno,
                                       Integer antecedenciaCancelamentoHoras, Integer antecedenciaAgendamentoHoras,
                                       Boolean cobrarFalta, BigDecimal valorFalta, Integer lembreteHorasAntes,
                                       Integer simuladoQtdQuestoes, Integer simuladoTempoMin,
                                       Integer simuladoAcertosMinimos, String fusoHorario, Long version) {
        public static ConfiguracaoResponse de(ConfiguracaoAutoescola c) {
            return new ConfiguracaoResponse(c.getDuracaoAulaMin(), c.getMaxAulasDiaAluno(),
                    c.getAntecedenciaCancelamentoHoras(), c.getAntecedenciaAgendamentoHoras(), c.isCobrarFalta(),
                    c.getValorFalta(), c.getLembreteHorasAntes(), c.getSimuladoQtdQuestoes(),
                    c.getSimuladoTempoMin(), c.getSimuladoAcertosMinimos(), c.getFusoHorario(), c.getVersion());
        }
    }

    public record RegraCategoriaRequest(
            @NotNull @DecimalMin("0.0") @DecimalMax("999.9") BigDecimal horasPraticasMinimas,
            @NotNull @DecimalMin("0.0") @DecimalMax("999.9") BigDecimal horasTeoricasMinimas,
            @NotNull @Min(16) @Max(99) Integer idadeMinima,
            @NotNull Boolean ativa) {
    }

    public record RegraCategoriaResponse(Long id, Categoria categoria, BigDecimal horasPraticasMinimas,
                                         BigDecimal horasTeoricasMinimas, int idadeMinima, boolean ativa) {
        public static RegraCategoriaResponse de(RegraCategoria r) {
            return new RegraCategoriaResponse(r.getId(), r.getCategoria(), r.getHorasPraticasMinimas(),
                    r.getHorasTeoricasMinimas(), r.getIdadeMinima(), r.isAtiva());
        }
    }
}
