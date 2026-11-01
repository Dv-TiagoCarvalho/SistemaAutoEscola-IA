-- Fase 3: instrutores, veículos, aulas práticas e registro da aula.

ALTER TABLE configuracao_autoescola
    ADD COLUMN exigir_exames_aulas BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE instrutor (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT       NOT NULL,
    usuario_id    BIGINT,
    nome          VARCHAR(120) NOT NULL,
    telefone      VARCHAR(20),
    cnh_validade  DATE,
    categorias    VARCHAR(40)  NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    criado_por    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_instrutor_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_instrutor_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_instrutor_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_instrutor_nome ON instrutor (autoescola_id, nome);

CREATE TABLE veiculo (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT      NOT NULL,
    placa         VARCHAR(7)  NOT NULL,
    modelo        VARCHAR(80) NOT NULL,
    ano           INT         NOT NULL,
    categoria     VARCHAR(5)  NOT NULL,
    ativo         BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    criado_por    BIGINT,
    version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_veiculo_placa UNIQUE (autoescola_id, placa),
    CONSTRAINT fk_veiculo_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE aula (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id        BIGINT       NOT NULL,
    aluno_id             BIGINT       NOT NULL,
    matricula_id         BIGINT       NOT NULL,
    instrutor_id         BIGINT       NOT NULL,
    veiculo_id           BIGINT       NOT NULL,
    inicio               DATETIME(6)  NOT NULL,
    fim                  DATETIME(6)  NOT NULL,
    status               VARCHAR(15)  NOT NULL,
    motivo_cancelamento  VARCHAR(300),
    cancelamento_tardio  BOOLEAN      NOT NULL DEFAULT FALSE,
    km_inicial           INT,
    km_final             INT,
    conteudo             VARCHAR(300),
    observacoes          VARCHAR(500),
    nota                 INT,
    registrada_em        DATETIME(6),
    registrada_por       BIGINT,
    criado_em            DATETIME(6)  NOT NULL,
    atualizado_em        DATETIME(6)  NOT NULL,
    criado_por           BIGINT,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_aula_periodo CHECK (fim > inicio),
    CONSTRAINT ck_aula_km CHECK (km_final IS NULL OR km_inicial IS NULL OR km_final >= km_inicial),
    CONSTRAINT ck_aula_nota CHECK (nota IS NULL OR (nota BETWEEN 1 AND 5)),
    CONSTRAINT fk_aula_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_aula_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id),
    CONSTRAINT fk_aula_matricula FOREIGN KEY (matricula_id) REFERENCES matricula (id),
    CONSTRAINT fk_aula_instrutor FOREIGN KEY (instrutor_id) REFERENCES instrutor (id),
    CONSTRAINT fk_aula_veiculo FOREIGN KEY (veiculo_id) REFERENCES veiculo (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_aula_instrutor ON aula (autoescola_id, instrutor_id, inicio);
CREATE INDEX ix_aula_veiculo ON aula (autoescola_id, veiculo_id, inicio);
CREATE INDEX ix_aula_aluno ON aula (autoescola_id, aluno_id, inicio);
CREATE INDEX ix_aula_matricula ON aula (matricula_id, status);
