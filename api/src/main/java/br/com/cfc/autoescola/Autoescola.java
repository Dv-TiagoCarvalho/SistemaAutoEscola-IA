package br.com.cfc.autoescola;

import br.com.cfc.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** O tenant do SaaS: cada autoescola cliente. */
@Getter
@Setter
@Entity
@Table(name = "autoescola")
public class Autoescola extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    /** Só dígitos (14). */
    @Column(nullable = false, length = 14, unique = true)
    private String cnpj;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(length = 20)
    private String telefone;

    @Column(length = 160)
    private String email;

    @Column(nullable = false)
    private boolean ativo = true;
}
