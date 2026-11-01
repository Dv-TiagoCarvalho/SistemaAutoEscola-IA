package br.com.cfc.aluno;

import br.com.cfc.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "aluno")
public class Aluno extends TenantEntity {

    /** Preenchido quando o aluno ganha acesso ao app. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 120)
    private String nome;

    /** Só dígitos (11). */
    @Column(nullable = false, length = 11)
    private String cpf;

    @Column(length = 20)
    private String rg;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    /** Só dígitos (10 ou 11). */
    @Column(nullable = false, length = 20)
    private String telefone;

    @Column(length = 160)
    private String email;

    @Column(length = 8)
    private String cep;

    @Column(length = 150)
    private String logradouro;

    @Column(length = 10)
    private String numero;

    @Column(length = 60)
    private String complemento;

    @Column(length = 80)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(nullable = false)
    private boolean ativo = true;
}
