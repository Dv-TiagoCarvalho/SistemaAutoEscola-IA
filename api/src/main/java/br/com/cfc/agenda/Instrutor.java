package br.com.cfc.agenda;

import br.com.cfc.autoescola.Categoria;
import br.com.cfc.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "instrutor")
public class Instrutor extends TenantEntity {

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Column(name = "cnh_validade")
    private LocalDate cnhValidade;

    /** Categorias que o instrutor pode ensinar, separadas por vírgula (ex.: "A,B"). */
    @Column(nullable = false, length = 40)
    private String categorias = "";

    @Column(nullable = false)
    private boolean ativo = true;

    public Set<Categoria> categoriasSet() {
        Set<Categoria> r = EnumSet.noneOf(Categoria.class);
        if (categorias != null && !categorias.isBlank()) {
            for (String c : categorias.split(",")) {
                r.add(Categoria.valueOf(c.trim()));
            }
        }
        return r;
    }

    public void definirCategorias(Collection<Categoria> novas) {
        this.categorias = novas.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }
}
