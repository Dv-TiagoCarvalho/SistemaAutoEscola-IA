# Autoescola SaaS

Sistema de gestão para autoescolas (CFCs), multi-tenant: várias autoescolas no mesmo sistema, cada uma vendo só os próprios dados.

- **api/**: Java 21, Spring Boot 3.5, Spring Security (JWT + refresh token), JPA, MySQL 8, Flyway, Swagger
- **app/**: Flutter (Android, iOS e Web/PWA), Riverpod, go_router, Dio

## Rodar tudo com Docker

```bash
cp .env.example .env        # ajuste as senhas
docker compose up --build
```

- App web: http://localhost:8081
- Swagger: http://localhost:8080/swagger-ui.html

Usuários criados com `SEED_EXEMPLO=true` (senha `Senha@123` para todos):

| E-mail | Perfil | Autoescola |
|---|---|---|
| super@cfc.local | SUPER_ADMIN | (plataforma) |
| admin@cfcexemplo.com.br | ADMIN | CFC Exemplo |
| secretaria@cfcexemplo.com.br | SECRETARIA | CFC Exemplo |
| instrutor@cfcexemplo.com.br | INSTRUTOR | CFC Exemplo |
| admin@cfcmodelo.com.br | ADMIN | CFC Modelo |

## Desenvolvimento

**API** (precisa de um MySQL rodando, ex.: `docker compose up mysql`):

```bash
cd api
mvn test                     # testes; o de integração usa Docker (Testcontainers) e é pulado sem Docker
SEED_EXEMPLO=true SUPERADMIN_EMAIL=super@cfc.local SUPERADMIN_SENHA=Senha@123 \
DB_PASSWORD=<senha do .env> mvn spring-boot:run
```

**App:**

```bash
cd app
flutter create . --platforms=android,ios,web --org br.com.cfc   # só na 1ª vez: gera as pastas de plataforma
flutter pub get
flutter test
flutter run -d chrome --dart-define=API_URL=http://localhost:8080
flutter run -d <celular> --dart-define=API_URL=http://<ip-do-seu-mac>:8080
```

No emulador Android, use `http://10.0.2.2:8080`. Ao rodar fora do Docker, inclua a origem do Flutter web em `CORS_ORIGINS`
(ex.: `flutter run -d chrome --web-port 5000` e `CORS_ORIGINS=http://localhost:5000`).

## Multi-tenant

- Toda entidade de negócio estende `TenantEntity`, que tem `autoescola_id` com `@TenantId` do Hibernate:
  o filtro por autoescola é automático em consultas e inserts.
- O tenant vem do JWT (`JwtAuthenticationFilter` → `TenantContext` → `TenantIdentifierResolver`).
- SUPER_ADMIN usa o tenant "raiz" (enxerga tudo); requisição sem login usa um tenant inexistente (não enxerga nada).
- **SQL nativo e JdbcTemplate não passam pelo filtro**: sempre filtre `autoescola_id` manualmente.
- `usuario`, `autoescola` e `audit_log` não usam `@TenantId` (login e SUPER_ADMIN precisam enxergar além do tenant);
  os services filtram explicitamente.

## Regras legais

Carga horária mínima e idade por categoria ficam na tabela `regra_categoria`, editável por autoescola.
Os valores iniciais são só ponto de partida: a Resolução Contran 1.020/2025 mudou as exigências e o tema
ainda está em discussão. Cada autoescola deve conferir os valores vigentes.

## Fase 2 — Alunos, matrículas e pacotes

**Back-end** (`/api`): alunos (CPF validado, único por autoescola), processos de habilitação (idade mínima e categoria ativa
vêm de `regra_categoria`; alertas de exame e LADV), documentos (JPEG/PNG/PDF até 10 MB, tipo conferido pelo conteúdo),
consentimentos LGPD, pacotes, matrículas (parcelamento em centavos, créditos de aula, aula avulsa, cancelamento) e contrato em PDF.
Arquivos ficam em `STORAGE_PATH` (volume `uploads` no Docker); a interface `StorageService` permite trocar por S3 depois.

**Atenção:** as cláusulas do contrato são genéricas. Peça a um advogado para revisar antes de usar com clientes reais.

**Testar:** `cd api && mvn test` (precisa de Docker para o teste com Testcontainers) e `cd app && flutter test`.
Depois `docker compose up --build`, entre como `admin@cfcexemplo.com.br` / `Senha@123` (com `SEED_EXEMPLO=true`) e use Alunos > Novo aluno
> Processos > Matricular > Contrato (PDF). Swagger em `/swagger-ui.html`.

## Fase 3 — Agenda e registro de aulas

**Back-end:** instrutores (com acesso opcional ao app), veículos, aulas práticas e registro da aula.
Regras da agenda (`AulaService`): sem conflito de instrutor, veículo ou aluno; a aula consome um crédito da matrícula
(pacote + avulsas); duração, antecedência de agendamento/cancelamento, limite diário e cobrança de falta vêm da
configuração da autoescola; categoria do veículo e do instrutor precisam bater com a do processo (AB aceita veículo A ou B);
exames médico e psicológico APTOS e LADV válida na data (a exigência de exames é a coluna `exigir_exames_aulas`, ligada por padrão).
Falta só consome crédito quando a autoescola **não** cobra falta; cancelamento tardio gera título de falta se houver cobrança.
O instrutor registra a aula (realizada/falta, km, conteúdo, nota) só das próprias aulas; o aluno só cancela as próprias.

**App:** aba Agenda (dia a dia para a equipe; "próximas aulas" para instrutor e aluno) e aba Cadastros (pacotes, instrutores, veículos e equipe).

**Pendente na Fase 3:** a opção "exigir exames" ainda não aparece na tela de Configurações (só no banco).

## Fase 4 — Financeiro

Baixa de títulos (forma, data, multa/juros e desconto), recibo numerado em PDF (numeração por autoescola, sem repetir),
estorno (ADMIN; reabre o título e mantém o recibo marcado como estornado), cancelamento de título, títulos avulsos,
despesas (ADMIN) e resumo com fluxo dos últimos 6 meses. `PaymentProvider` é a interface para integrar gateway de
pagamento (hoje só existe o `ManualPaymentProvider`, que registra o recebimento no balcão). O recibo não é nota fiscal.

## Fase 5 — Exames e frota

Exames teórico/prático por processo: prático só depois de aprovado no teórico, contagem automática de tentativas, aprovação
no prático encerra o processo, reprovação volta para "Em aulas". Resultado lançado manualmente (sem integração com órgão de trânsito).
Frota: validade de licenciamento e seguro, revisão por km, histórico de manutenção e alertas; o km do veículo sobe sozinho
quando o instrutor registra a aula ou uma manutenção. Pendente: disponibilidade semanal do instrutor.
