-- Fase 6: banco de questões, aulas teóricas com presença e simulados.

CREATE TABLE questao (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT        NOT NULL,
    tema          VARCHAR(60)   NOT NULL,
    enunciado     VARCHAR(1000) NOT NULL,
    alt_a         VARCHAR(400)  NOT NULL,
    alt_b         VARCHAR(400)  NOT NULL,
    alt_c         VARCHAR(400)  NOT NULL,
    alt_d         VARCHAR(400)  NOT NULL,
    correta       VARCHAR(1)    NOT NULL,
    explicacao    VARCHAR(600),
    ativa         BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(6)   NOT NULL,
    atualizado_em DATETIME(6)   NOT NULL,
    criado_por    BIGINT,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_questao_correta CHECK (correta IN ('A', 'B', 'C', 'D')),
    CONSTRAINT fk_questao_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_questao_ativa ON questao (autoescola_id, ativa, tema);

CREATE TABLE aula_teorica (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT       NOT NULL,
    tema          VARCHAR(120) NOT NULL,
    instrutor_id  BIGINT,
    inicio        DATETIME(6)  NOT NULL,
    fim           DATETIME(6)  NOT NULL,
    local         VARCHAR(120),
    status        VARCHAR(12)  NOT NULL DEFAULT 'AGENDADA',
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    criado_por    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_aula_teorica_periodo CHECK (fim > inicio),
    CONSTRAINT fk_aula_teorica_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_aula_teorica_instrutor FOREIGN KEY (instrutor_id) REFERENCES instrutor (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_aula_teorica_inicio ON aula_teorica (autoescola_id, inicio);

CREATE TABLE presenca_teorica (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    autoescola_id    BIGINT      NOT NULL,
    aula_teorica_id  BIGINT      NOT NULL,
    aluno_id         BIGINT      NOT NULL,
    presente         BOOLEAN     NOT NULL,
    criado_em        DATETIME(6) NOT NULL,
    atualizado_em    DATETIME(6) NOT NULL,
    criado_por       BIGINT,
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_presenca UNIQUE (aula_teorica_id, aluno_id),
    CONSTRAINT fk_presenca_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_presenca_aula FOREIGN KEY (aula_teorica_id) REFERENCES aula_teorica (id),
    CONSTRAINT fk_presenca_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE simulado (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    autoescola_id    BIGINT      NOT NULL,
    aluno_id         BIGINT      NOT NULL,
    iniciado_em      DATETIME(6) NOT NULL,
    limite_em        DATETIME(6) NOT NULL,
    finalizado_em    DATETIME(6),
    qtd_questoes     INT         NOT NULL,
    acertos_minimos  INT         NOT NULL,
    acertos          INT,
    aprovado         BOOLEAN,
    expirado         BOOLEAN     NOT NULL DEFAULT FALSE,
    criado_em        DATETIME(6) NOT NULL,
    atualizado_em    DATETIME(6) NOT NULL,
    criado_por       BIGINT,
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_simulado_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_simulado_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_simulado_aluno ON simulado (autoescola_id, aluno_id, iniciado_em);

CREATE TABLE simulado_questao (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT      NOT NULL,
    simulado_id   BIGINT      NOT NULL,
    questao_id    BIGINT      NOT NULL,
    ordem         INT         NOT NULL,
    resposta      VARCHAR(1),
    correta       BOOLEAN,
    criado_em     DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    criado_por    BIGINT,
    version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_simulado_questao UNIQUE (simulado_id, questao_id),
    CONSTRAINT fk_sq_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_sq_simulado FOREIGN KEY (simulado_id) REFERENCES simulado (id),
    CONSTRAINT fk_sq_questao FOREIGN KEY (questao_id) REFERENCES questao (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
