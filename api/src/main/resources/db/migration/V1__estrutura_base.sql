-- Fase 1: plataforma, autenticação, configurações e auditoria.
-- Convenções: datas em UTC (DATETIME(6)), dinheiro em DECIMAL(12,2), enums como VARCHAR.

CREATE TABLE autoescola (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(150) NOT NULL,
    cnpj          VARCHAR(14)  NOT NULL,
    uf            VARCHAR(2)   NOT NULL,
    cidade        VARCHAR(100) NOT NULL,
    telefone      VARCHAR(20),
    email         VARCHAR(160),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    criado_por    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_autoescola_cnpj UNIQUE (cnpj)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE configuracao_autoescola (
    autoescola_id                   BIGINT        NOT NULL,
    duracao_aula_min                INT           NOT NULL DEFAULT 50,
    max_aulas_dia_aluno             INT           NOT NULL DEFAULT 2,
    antecedencia_cancelamento_horas INT           NOT NULL DEFAULT 24,
    antecedencia_agendamento_horas  INT           NOT NULL DEFAULT 2,
    cobrar_falta                    BOOLEAN       NOT NULL DEFAULT FALSE,
    valor_falta                     DECIMAL(12,2) NOT NULL DEFAULT 0,
    lembrete_horas_antes            INT           NOT NULL DEFAULT 24,
    simulado_qtd_questoes           INT           NOT NULL DEFAULT 30,
    simulado_tempo_min              INT           NOT NULL DEFAULT 40,
    simulado_acertos_minimos        INT           NOT NULL DEFAULT 21,
    fuso_horario                    VARCHAR(40)   NOT NULL DEFAULT 'America/Sao_Paulo',
    atualizado_em                   DATETIME(6)   NOT NULL,
    version                         BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (autoescola_id),
    CONSTRAINT fk_config_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE regra_categoria (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id          BIGINT       NOT NULL,
    categoria              VARCHAR(5)   NOT NULL,
    horas_praticas_minimas DECIMAL(5,1) NOT NULL,
    horas_teoricas_minimas DECIMAL(5,1) NOT NULL,
    idade_minima           INT          NOT NULL,
    ativa                  BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em              DATETIME(6)  NOT NULL,
    atualizado_em          DATETIME(6)  NOT NULL,
    criado_por             BIGINT,
    version                BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_regra_categoria UNIQUE (autoescola_id, categoria),
    CONSTRAINT fk_regra_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE usuario (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT,
    nome          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    senha_hash    VARCHAR(100) NOT NULL,
    perfil        VARCHAR(20)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login  DATETIME(6),
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    criado_por    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT fk_usuario_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    -- Só o SUPER_ADMIN pode existir sem autoescola.
    CONSTRAINT ck_usuario_tenant CHECK ((perfil = 'SUPER_ADMIN' AND autoescola_id IS NULL)
                                     OR (perfil <> 'SUPER_ADMIN' AND autoescola_id IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_usuario_autoescola_perfil ON usuario (autoescola_id, perfil);

CREATE TABLE refresh_token (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expira_em   DATETIME(6) NOT NULL,
    criado_em   DATETIME(6) NOT NULL,
    revogado_em DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_refresh_usuario ON refresh_token (usuario_id, revogado_em);
CREATE INDEX ix_refresh_expira ON refresh_token (expira_em);

CREATE TABLE audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT,
    usuario_id    BIGINT,
    usuario_nome  VARCHAR(120),
    entidade      VARCHAR(60)  NOT NULL,
    entidade_id   BIGINT,
    acao          VARCHAR(20)  NOT NULL,
    dados_antes   JSON,
    dados_depois  JSON,
    ip            VARCHAR(45),
    data_hora     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_audit_autoescola_data ON audit_log (autoescola_id, data_hora);
CREATE INDEX ix_audit_entidade ON audit_log (entidade, entidade_id);
