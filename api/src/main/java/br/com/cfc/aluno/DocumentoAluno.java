package br.com.cfc.aluno;

import br.com.cfc.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "documento_aluno")
public class DocumentoAluno extends TenantEntity {

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private TipoDocumento tipo;

    /** Nome que o usuário enviou. Só para exibição: o arquivo em disco usa um nome gerado. */
    @Column(name = "nome_original", nullable = false, length = 200)
    private String nomeOriginal;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(nullable = false)
    private long tamanho;

    @Column(nullable = false, length = 300)
    private String caminho;
}
