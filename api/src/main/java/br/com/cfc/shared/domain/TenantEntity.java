package br.com.cfc.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base de toda tabela de negócio de uma autoescola.
 * <p>
 * O {@link TenantId} faz o Hibernate:
 * <ul>
 *   <li>adicionar {@code autoescola_id = :tenant} automaticamente em TODA consulta da entidade;</li>
 *   <li>preencher {@code autoescola_id} automaticamente em todo INSERT.</li>
 * </ul>
 * O tenant atual vem do {@code TenantIdentifierResolver}, que lê o usuário do JWT.
 * Consultas nativas (SQL puro) NÃO passam por esse filtro e precisam filtrar manualmente.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "autoescola_id", nullable = false, updatable = false)
    private Long autoescolaId;
}
