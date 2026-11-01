-- Fase 2: alunos, processos de habilitação, documentos, LGPD, pacotes, matrículas, créditos de aula e títulos a receber.

CREATE TABLE aluno (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id   BIGINT       NOT NULL,
    usuario_id      BIGINT,
    nome            VARCHAR(120) NOT NULL,
    cpf             VARCHAR(11)  NOT NULL,
    rg              VARCHAR(20),
    data_nascimento DATE         NOT NULL,
    telefone        VARCHAR(20)  NOT NULL,
    email           VARCHAR(160),
    cep             VARCHAR(8),
    logradouro      VARCHAR(150),
    numero          VARCHAR(10),
    complemento     VARCHAR(60),
    bairro          VARCHAR(80),
    cidade          VARCHAR(100),
    uf              VARCHAR(2),
    ativo           BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em       DATETIME(6)  NOT NULL,
    atualizado_em   DATETIME(6)  NOT NULL,
    criado_por      BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_aluno_cpf UNIQUE (autoescola_id, cpf),
    CONSTRAINT uk_aluno_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_aluno_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_aluno_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_aluno_nome ON aluno (autoescola_id, nome);

CREATE TABLE processo_habilitacao (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    autoescola_id     BIGINT      NOT NULL,
    aluno_id          BIGINT      NOT NULL,
    categoria         VARCHAR(5)  NOT NULL,
    tipo              VARCHAR(30) NOT NULL,
    renach            VARCHAR(20),
    exame_medico      VARCHAR(10) NOT NULL DEFAULT 'PENDENTE',
    exame_psicologico VARCHAR(10) NOT NULL DEFAULT 'PENDENTE',
    ladv_numero       VARCHAR(30),
    ladv_validade     DATE,
    situacao          VARCHAR(20) NOT NULL DEFAULT 'MATRICULADO',
    observacoes       VARCHAR(500),
    criado_em         DATETIME(6) NOT NULL,
    atualizado_em     DATETIME(6) NOT NULL,
    criado_por        BIGINT,
    version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_processo_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_processo_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_processo_aluno ON processo_habilitacao (autoescola_id, aluno_id);
CREATE INDEX ix_processo_situacao ON processo_habilitacao (autoescola_id, situacao);

CREATE TABLE documento_aluno (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT       NOT NULL,
    aluno_id      BIGINT       NOT NULL,
    tipo          VARCHAR(30)  NOT NULL,
    nome_original VARCHAR(200) NOT NULL,
    content_type  VARCHAR(60)  NOT NULL,
    tamanho       BIGINT       NOT NULL,
    caminho       VARCHAR(300) NOT NULL,
    criado_em     DATETIME(6)  NOT NULL,
    atualizado_em DATETIME(6)  NOT NULL,
    criado_por    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_documento_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_documento_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_documento_aluno ON documento_aluno (autoescola_id, aluno_id);

CREATE TABLE consentimento_lgpd (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    autoescola_id BIGINT      NOT NULL,
    aluno_id      BIGINT      NOT NULL,
    versao_termo  VARCHAR(20) NOT NULL,
    aceito_em     DATETIME(6) NOT NULL,
    ip            VARCHAR(45),
    revogado_em   DATETIME(6),
    criado_em     DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    criado_por    BIGINT,
    version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_consent_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_consent_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_consent_aluno ON consentimento_lgpd (autoescola_id, aluno_id);

CREATE TABLE pacote (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id       BIGINT        NOT NULL,
    categoria           VARCHAR(5)    NOT NULL,
    nome                VARCHAR(100)  NOT NULL,
    qtd_aulas_praticas  INT           NOT NULL,
    teorico_incluso     BOOLEAN       NOT NULL DEFAULT TRUE,
    preco               DECIMAL(12,2) NOT NULL,
    ativo               BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em           DATETIME(6)   NOT NULL,
    atualizado_em       DATETIME(6)   NOT NULL,
    criado_por          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_pacote_preco CHECK (preco >= 0),
    CONSTRAINT ck_pacote_aulas CHECK (qtd_aulas_praticas >= 0),
    CONSTRAINT fk_pacote_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_pacote_categoria ON pacote (autoescola_id, categoria, ativo);

-- A matrícula guarda uma cópia (snapshot) dos dados do pacote: mudar o pacote depois não altera contratos já assinados.
CREATE TABLE matricula (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id       BIGINT        NOT NULL,
    processo_id         BIGINT        NOT NULL,
    aluno_id            BIGINT        NOT NULL,
    pacote_id           BIGINT        NOT NULL,
    categoria           VARCHAR(5)    NOT NULL,
    pacote_nome         VARCHAR(100)  NOT NULL,
    aulas_praticas      INT           NOT NULL,
    teorico_incluso     BOOLEAN       NOT NULL,
    data_matricula      DATE          NOT NULL,
    valor_bruto         DECIMAL(12,2) NOT NULL,
    desconto            DECIMAL(12,2) NOT NULL DEFAULT 0,
    valor_total         DECIMAL(12,2) NOT NULL,
    status              VARCHAR(15)   NOT NULL DEFAULT 'ATIVA',
    motivo_cancelamento VARCHAR(300),
    cancelada_em        DATETIME(6),
    contrato_path       VARCHAR(300),
    criado_em           DATETIME(6)   NOT NULL,
    atualizado_em       DATETIME(6)   NOT NULL,
    criado_por          BIGINT,
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_matricula_valores CHECK (desconto >= 0 AND valor_total >= 0 AND desconto <= valor_bruto),
    CONSTRAINT fk_matricula_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_matricula_processo FOREIGN KEY (processo_id) REFERENCES processo_habilitacao (id),
    CONSTRAINT fk_matricula_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id),
    CONSTRAINT fk_matricula_pacote FOREIGN KEY (pacote_id) REFERENCES pacote (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_matricula_aluno ON matricula (autoescola_id, aluno_id);
CREATE INDEX ix_matricula_processo ON matricula (processo_id, status);

-- O saldo de aulas = soma dos créditos - aulas consumidas (Fase 3).
CREATE TABLE credito_aula (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id  BIGINT        NOT NULL,
    matricula_id   BIGINT        NOT NULL,
    aluno_id       BIGINT        NOT NULL,
    origem         VARCHAR(10)   NOT NULL,
    quantidade     INT           NOT NULL,
    valor_unitario DECIMAL(12,2),
    descricao      VARCHAR(120),
    criado_em      DATETIME(6)   NOT NULL,
    atualizado_em  DATETIME(6)   NOT NULL,
    criado_por     BIGINT,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_credito_qtd CHECK (quantidade > 0),
    CONSTRAINT fk_credito_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_credito_matricula FOREIGN KEY (matricula_id) REFERENCES matricula (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_credito_matricula ON credito_aula (matricula_id);
CREATE INDEX ix_credito_aluno ON credito_aula (autoescola_id, aluno_id);

-- Parcelas a receber. Baixa (pagamento) e recibo entram na Fase 4.
CREATE TABLE titulo_receber (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    autoescola_id  BIGINT        NOT NULL,
    aluno_id       BIGINT        NOT NULL,
    matricula_id   BIGINT,
    origem         VARCHAR(15)   NOT NULL,
    descricao      VARCHAR(120)  NOT NULL,
    numero_parcela INT           NOT NULL,
    total_parcelas INT           NOT NULL,
    valor          DECIMAL(12,2) NOT NULL,
    vencimento     DATE          NOT NULL,
    status         VARCHAR(10)   NOT NULL DEFAULT 'ABERTO',
    criado_em      DATETIME(6)   NOT NULL,
    atualizado_em  DATETIME(6)   NOT NULL,
    criado_por     BIGINT,
    version        BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_titulo_valor CHECK (valor > 0),
    CONSTRAINT fk_titulo_autoescola FOREIGN KEY (autoescola_id) REFERENCES autoescola (id),
    CONSTRAINT fk_titulo_aluno FOREIGN KEY (aluno_id) REFERENCES aluno (id),
    CONSTRAINT fk_titulo_matricula FOREIGN KEY (matricula_id) REFERENCES matricula (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_titulo_matricula ON titulo_receber (matricula_id);
CREATE INDEX ix_titulo_aberto ON titulo_receber (autoescola_id, status, vencimento);
CREATE INDEX ix_titulo_aluno ON titulo_receber (autoescola_id, aluno_id);
