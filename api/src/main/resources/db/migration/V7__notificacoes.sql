-- Fase 7: notificações dentro do app (lembretes de aula, avisos).

CREATE TABLE notificacao (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT        NOT NULL,
    usuario_id    BIGINT        NOT NULL,
    titulo        VARCHAR(120)  NOT NULL,
    mensagem      VARCHAR(400)  NOT NULL,
    chave         VARCHAR(80),
    lida          BOOLEAN       NOT NULL DEFAULT FALSE,
    criado_em     DATETIME(6)   NOT NULL,
    atualizado_em DATETIME(6)   NOT NULL,
    criado_por    BIGINT,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- chave evita duplicar o mesmo lembrete (ex.: "aula-123"); NULL não conflita.
    CONSTRAINT uk_notificacao_chave UNIQUE (usuario_id, chave),
    CONSTRAINT fk_notificacao_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_notificacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_notificacao_usuario ON notificacao (autoescola_id, usuario_id, lida, criado_em);
