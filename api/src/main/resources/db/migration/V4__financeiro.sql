-- Fase 4: pagamentos (baixa de títulos), recibos numerados e despesas.

ALTER TABLE configuracao_autoescola ADD COLUMN proximo_recibo BIGINT NOT NULL DEFAULT 1;

CREATE TABLE pagamento (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id      BIGINT        NOT NULL,
    titulo_id          BIGINT        NOT NULL,
    aluno_id           BIGINT        NOT NULL,
    valor              DECIMAL(12,2) NOT NULL,
    acrescimo          DECIMAL(12,2) NOT NULL DEFAULT 0,
    desconto           DECIMAL(12,2) NOT NULL DEFAULT 0,
    forma              VARCHAR(20)   NOT NULL,
    data_pagamento     DATE          NOT NULL,
    recibo_numero      VARCHAR(12)   NOT NULL,
    provider           VARCHAR(20)   NOT NULL,
    referencia_externa VARCHAR(80),
    estornado          BOOLEAN       NOT NULL DEFAULT FALSE,
    estornado_em       DATETIME(6),
    motivo_estorno     VARCHAR(300),
    criado_em          DATETIME(6)   NOT NULL,
    atualizado_em      DATETIME(6)   NOT NULL,
    criado_por         BIGINT,
    version            BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pagamento_recibo UNIQUE (autoescola_id, recibo_numero),
    CONSTRAINT ck_pagamento_valor CHECK (valor > 0 AND acrescimo >= 0 AND desconto >= 0),
    CONSTRAINT fk_pagamento_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_pagamento_titulo FOREIGN KEY (titulo_id) REFERENCES titulo_receber (id),
    CONSTRAINT fk_pagamento_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_pagamento_data ON pagamento (autoescola_id, data_pagamento);
CREATE INDEX ix_pagamento_titulo ON pagamento (titulo_id);
CREATE INDEX ix_titulo_vencimento ON titulo_receber (autoescola_id, status, vencimento);
CREATE INDEX ix_titulo_aluno ON titulo_receber (autoescola_id, aluno_id);

CREATE TABLE despesa (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id  BIGINT        NOT NULL,
    descricao      VARCHAR(120)  NOT NULL,
    categoria      VARCHAR(20)   NOT NULL,
    valor          DECIMAL(12,2) NOT NULL,
    vencimento     DATE          NOT NULL,
    data_pagamento DATE,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ABERTA',
    criado_em      DATETIME(6)   NOT NULL,
    atualizado_em  DATETIME(6)   NOT NULL,
    criado_por     BIGINT,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_despesa_valor CHECK (valor > 0),
    CONSTRAINT fk_despesa_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_despesa_venc ON despesa (autoescola_id, vencimento);
