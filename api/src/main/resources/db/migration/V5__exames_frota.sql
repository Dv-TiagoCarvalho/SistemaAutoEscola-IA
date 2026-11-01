-- Fase 5: exames (teórico e prático) e frota (documentos e manutenção dos veículos).

CREATE TABLE exame (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT       NOT NULL,
    aluno_id      BIGINT       NOT NULL,
    processo_id   BIGINT       NOT NULL,
    tipo          VARCHAR(10)  NOT NULL,
    data_hora     DATETIME(6)  NOT NULL,
    local         VARCHAR(120),
    tentativa     INT          NOT NULL,
    resultado     VARCHAR(12)  NOT NULL DEFAULT 'AGENDADO',
    observacoes   VARCHAR(300),
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    criado_por    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_exame_tentativa CHECK (tentativa >= 1),
    CONSTRAINT fk_exame_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_exame_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id),
    CONSTRAINT fk_exame_processo FOREIGN KEY (processo_id) REFERENCES processo_habilitacao (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_exame_data ON exame (autoescola_id, data_hora);
CREATE INDEX ix_exame_processo ON exame (processo_id, tipo);

ALTER TABLE veiculo
    ADD COLUMN km_atual INT NOT NULL DEFAULT 0,
    ADD COLUMN licenciamento_validade DATE,
    ADD COLUMN seguro_validade DATE,
    ADD COLUMN proxima_revisao_km INT;

CREATE TABLE manutencao_veiculo (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT        NOT NULL,
    veiculo_id    BIGINT        NOT NULL,
    data          DATE          NOT NULL,
    descricao     VARCHAR(200)  NOT NULL,
    km            INT,
    valor         DECIMAL(12,2) NOT NULL DEFAULT 0,
    criado_em     DATETIME(6)   NOT NULL,
    atualizado_em DATETIME(6)   NOT NULL,
    criado_por    BIGINT,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_manutencao_valor CHECK (valor >= 0),
    CONSTRAINT fk_manutencao_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_manutencao_veiculo FOREIGN KEY (veiculo_id) REFERENCES veiculo (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_manutencao_veiculo ON manutencao_veiculo (veiculo_id, data);
