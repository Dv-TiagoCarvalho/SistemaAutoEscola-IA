package br.com.cfc.financeiro;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Baixa de um título. Estornar não apaga: marca como estornado e reabre o título. */
@Getter
@Setter
@Entity
@Table(name = "pagamento")
public class Pagamento extends TenantEntity {

    @Column(name = "titulo_id", nullable = false)
    private Long tituloId;

    @Column(name = "aluno_id", nullable = false)
    private Long alunoId;

    /** Valor efetivamente pago = valor do título + acréscimo - desconto. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal acrescimo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FormaPagamento forma;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDate dataPagamento;

    @Column(name = "recibo_numero", nullable = false, length = 12)
    private String reciboNumero;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "referencia_externa", length = 80)
    private String referenciaExterna;

    @Column(nullable = false)
    private boolean estornado;

    @Column(name = "estornado_em")
    private Instant estornadoEm;

    @Column(name = "motivo_estorno", length = 300)
    private String motivoEstorno;
}
