package br.com.cfc.aluno;

import br.com.cfc.autoescola.AutoescolaDtos;
import br.com.cfc.autoescola.Categoria;
import br.com.cfc.shared.validation.Cpf;
import br.com.cfc.usuario.SenhaPolitica;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class AlunoDtos {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AlunoDtos() {
    }

    // ---------- Aluno ----------

    public record AlunoRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120, message = "Nome com no máximo 120 caracteres.") String nome,
            @NotBlank(message = "Informe o CPF.") @Cpf String cpf,
            @Size(max = 20, message = "RG com no máximo 20 caracteres.") String rg,
            @NotNull(message = "Informe a data de nascimento.") @Past(message = "A data de nascimento deve estar no passado.") LocalDate dataNascimento,
            @NotBlank(message = "Informe o telefone.") @Size(max = 20, message = "Telefone inválido.") String telefone,
            @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @Pattern(regexp = "^(\\d{8})?$", message = "CEP deve ter 8 números.") String cep,
            @Size(max = 150) String logradouro,
            @Size(max = 10) String numero,
            @Size(max = 60) String complemento,
            @Size(max = 80) String bairro,
            @Size(max = 100) String cidade,
            @Pattern(regexp = "^(" + AutoescolaDtos.REGEX_UF + ")?$", message = "UF inválida.") String uf) {
    }

    public record AlunoResponse(Long id, String nome, String cpf, String rg, LocalDate dataNascimento, String telefone,
                                String email, String cep, String logradouro, String numero, String complemento,
                                String bairro, String cidade, String uf, boolean ativo, boolean temAcesso,
                                Instant criadoEm) {
        public static AlunoResponse de(Aluno a) {
            return new AlunoResponse(a.getId(), a.getNome(), a.getCpf(), a.getRg(), a.getDataNascimento(),
                    a.getTelefone(), a.getEmail(), a.getCep(), a.getLogradouro(), a.getNumero(), a.getComplemento(),
                    a.getBairro(), a.getCidade(), a.getUf(), a.isAtivo(), a.getUsuarioId() != null, a.getCriadoEm());
        }
    }

    public record AlunoResumo(Long id, String nome, String cpf, String telefone, boolean ativo,
                              SituacaoProcesso situacaoAtual) {
        public static AlunoResumo de(Aluno a, SituacaoProcesso situacao) {
            return new AlunoResumo(a.getId(), a.getNome(), a.getCpf(), a.getTelefone(), a.isAtivo(), situacao);
        }
    }

    public record AlunoStatusRequest(@NotNull(message = "Informe se o aluno está ativo.") Boolean ativo) {
    }

    public record AcessoRequest(
            @NotBlank(message = "Informe o e-mail de acesso.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotBlank(message = "Informe a senha.") @Pattern(regexp = SenhaPolitica.REGEX, message = SenhaPolitica.MENSAGEM) String senha) {
    }

    /** Snapshot para o log de auditoria: sem CPF completo (minimização de dados, LGPD). */
    public record AlunoAuditoria(Long id, String nome, String cpf, String telefone, String email, boolean ativo,
                                 boolean temAcesso) {
        public static AlunoAuditoria de(Aluno a) {
            return new AlunoAuditoria(a.getId(), a.getNome(), br.com.cfc.shared.validation.Documentos.mascararCpf(a.getCpf()),
                    a.getTelefone(), a.getEmail(), a.isAtivo(), a.getUsuarioId() != null);
        }
    }

    // ---------- Processo de habilitação ----------

    public record ProcessoRequest(
            @NotNull(message = "Informe a categoria.") Categoria categoria,
            @NotNull(message = "Informe o tipo do processo.") TipoProcesso tipo,
            @Pattern(regexp = "^[A-Za-z0-9]{0,20}$", message = "RENACH deve ter só letras e números (até 20).") String renach,
            @Size(max = 500) String observacoes) {
    }

    public record ProcessoUpdateRequest(
            @Pattern(regexp = "^[A-Za-z0-9]{0,20}$", message = "RENACH deve ter só letras e números (até 20).") String renach,
            @NotNull(message = "Informe o status do exame médico.") StatusExame exameMedico,
            @NotNull(message = "Informe o status do exame psicológico.") StatusExame examePsicologico,
            @Size(max = 30) String ladvNumero,
            LocalDate ladvValidade,
            @NotNull(message = "Informe a situação.") SituacaoProcesso situacao,
            @Size(max = 500) String observacoes) {
    }

    public record ProcessoResponse(Long id, Long alunoId, Categoria categoria, TipoProcesso tipo, String renach,
                                   StatusExame exameMedico, StatusExame examePsicologico, String ladvNumero,
                                   LocalDate ladvValidade, SituacaoProcesso situacao, String observacoes,
                                   List<String> alertas, Instant criadoEm) {
        public static ProcessoResponse de(ProcessoHabilitacao p, LocalDate hoje) {
            return new ProcessoResponse(p.getId(), p.getAlunoId(), p.getCategoria(), p.getTipo(), p.getRenach(),
                    p.getExameMedico(), p.getExamePsicologico(), p.getLadvNumero(), p.getLadvValidade(),
                    p.getSituacao(), p.getObservacoes(), alertas(p, hoje), p.getCriadoEm());
        }

        /** Avisos para a secretaria. Não bloqueiam nada: os dados do Detran são lançados manualmente. */
        static List<String> alertas(ProcessoHabilitacao p, LocalDate hoje) {
            List<String> alertas = new ArrayList<>();
            if (p.getSituacao() == SituacaoProcesso.APROVADO || p.getSituacao() == SituacaoProcesso.DESISTENTE) {
                return alertas;
            }
            if (p.getExameMedico() == StatusExame.INAPTO) {
                alertas.add("Exame médico: inapto.");
            } else if (p.getExameMedico() != StatusExame.APTO) {
                alertas.add("Exame médico pendente.");
            }
            if (p.getExamePsicologico() == StatusExame.INAPTO) {
                alertas.add("Exame psicológico: inapto.");
            } else if (p.getExamePsicologico() != StatusExame.APTO) {
                alertas.add("Exame psicológico pendente.");
            }
            if (p.getLadvValidade() != null) {
                long dias = ChronoUnit.DAYS.between(hoje, p.getLadvValidade());
                if (dias < 0) {
                    alertas.add("LADV vencida em " + p.getLadvValidade().format(DATA_BR) + ".");
                } else if (dias <= 30) {
                    alertas.add("LADV vence em " + dias + " dia(s).");
                }
            }
            return alertas;
        }
    }

    // ---------- Documentos e LGPD ----------

    public record DocumentoResponse(Long id, Long alunoId, TipoDocumento tipo, String nomeOriginal,
                                    String contentType, long tamanho, Instant criadoEm) {
        public static DocumentoResponse de(DocumentoAluno d) {
            return new DocumentoResponse(d.getId(), d.getAlunoId(), d.getTipo(), d.getNomeOriginal(),
                    d.getContentType(), d.getTamanho(), d.getCriadoEm());
        }
    }

    public record ConsentimentoRequest(
            @NotBlank(message = "Informe a versão do termo.") @Size(max = 20, message = "Versão com no máximo 20 caracteres.") String versaoTermo) {
    }

    public record ConsentimentoResponse(Long id, Long alunoId, String versaoTermo, Instant aceitoEm, String ip,
                                        Instant revogadoEm, boolean vigente) {
        public static ConsentimentoResponse de(ConsentimentoLgpd c) {
            return new ConsentimentoResponse(c.getId(), c.getAlunoId(), c.getVersaoTermo(), c.getAceitoEm(),
                    c.getIp(), c.getRevogadoEm(), c.isVigente());
        }
    }
}
