# Ecommercie — API REST de e-commerce

API REST de e-commerce: catálogo, carrinho, pedidos, pagamento (Mercado Pago), frete
(Melhor Envio), devoluções, e-mail transacional via outbox e emissão fiscal (NF-e/DC-e).
O módulo fiscal **nasce desligado**.

Documentação de arquitetura em [`../docs/system-design.md`](../docs/system-design.md).
Contrato de resposta em [`../docs/patterns/api-response.md`](../docs/patterns/api-response.md).

---

## Stack

| | |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6 (Web MVC, Data JPA, Security, Validation, Actuator, Mail) |
| Banco | PostgreSQL 18 |
| Migrations | Flyway (`src/main/resources/db/migration`) — usado no perfil `docker` e nos testes |
| Auth | JWT próprio em **cookies httpOnly** (`access_token` + `refresh_token` com rotação) |
| Testes | JUnit 5 + MockMvc + **Testcontainers** (Postgres real, sem H2) |
| Docs | springdoc-openapi → Swagger UI |

---

## Dois jeitos de rodar

| | Local (perfil padrão) | Docker (perfil `docker`) |
|---|---|---|
| Como sobe | `./mvnw spring-boot:run` | `docker compose up --build` |
| Banco | Postgres **instalado na máquina**, `localhost:5432/ecommercie` (fixo no `application.yml`) | Postgres do compose, exposto no host em `localhost:5433` |
| Schema | `ddl-auto: update` (Hibernate), Flyway **desligado** | Flyway aplica as migrations, `ddl-auto: validate` |
| Cookie `secure` | `false` | `true` |
| Bom para | desenvolvimento e testes manuais (Postman) | conferir a imagem e o fluxo com migrations |

> Com `secure: true`, o navegador aceita os cookies em `http://localhost`, mas o **Postman pode
> não reenviá-los** por `http`. Para testar pelo Postman, use o modo local.

---

## 1. Configurar o `.env`

```bash
cp .env.example .env
```

Preencha o `.env` (tabela completa em [Variáveis de ambiente](#variáveis-de-ambiente)). No mínimo:

- `DB_PASSWORD` — senha do Postgres. No Docker o banco aceita qualquer senha (`trust`), mas a
  variável precisa existir.
- `JWT_SECRET` — gere com `openssl rand -base64 48`.
- `MP_ACCESS_TOKEN`, `MP_NOTIFICATION_URL` — credenciais de teste do Mercado Pago.
- `ME_TOKEN`, `ME_USER_AGENT`, `SHIPPING_ORIGIN_CEP` — credenciais do sandbox do Melhor Envio.

Essas variáveis não têm valor padrão: se faltarem, a aplicação **não sobe**, de propósito.

A aplicação lê o `.env` sozinha (`spring.config.import` no `application.yml`), desde que seja
iniciada **de dentro da pasta `ecommercie/`**. Não é preciso exportar as variáveis no shell.

---

## 2a. Subir localmente

Pré-requisitos: Java 21+ e um Postgres rodando em `localhost:5432` com um banco `ecommercie`
(usuário `postgres`, senha = `DB_PASSWORD`).

```bash
createdb -h localhost -p 5432 -U postgres ecommercie   # só na primeira vez
./mvnw spring-boot:run
```

No Windows com Git Bash, se o Java do `PATH` for mais antigo, aponte para o Corretto 24:

```bash
export JAVA_HOME=$(ls -d ~/.jdks/corretto-24* | head -1)
./mvnw spring-boot:run
```

Neste modo o Hibernate cria e atualiza as tabelas sozinho (`ddl-auto: update`).

## 2b. Subir com Docker

Pré-requisitos: Docker e Docker Compose.

```bash
docker compose up --build
```

As migrations do Flyway rodam no startup. `DB_NAME`, `DB_USER` e `CORS_ORIGIN` têm valor padrão
no `docker-compose.yml` (`ecommercie`, `postgres`, `http://localhost:4200`).

Para zerar o banco do Docker:

```bash
docker compose down -v && docker compose up --build
```

> O Postgres do compose sobe com `POSTGRES_HOST_AUTH_METHOD: trust` (sem senha). Isso é
> conveniente em desenvolvimento e **não deve ser usado em produção**.

## URLs

- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Health: <http://localhost:8080/actuator/health>

---

## 3. Criar o primeiro admin

Não existe endpoint para criar admin (de propósito). Cadastre um usuário normal e promova-o no banco.

1. Cadastre pelo `POST /api/v1/auth/register`:

   ```json
   { "nome": "Admin", "email": "admin@ecommercie.com", "senha": "troque-esta-senha", "cpf_cnpj": "00000000000" }
   ```

2. Promova para `ADMIN`:

   ```bash
   # modo local
   psql -h localhost -p 5432 -U postgres -d ecommercie -c "UPDATE usuarios SET papel = 'ADMIN' WHERE email = 'admin@ecommercie.com';"

   # modo Docker
   docker exec ecommercie-postgres psql -U postgres -d ecommercie -c "UPDATE usuarios SET papel = 'ADMIN' WHERE email = 'admin@ecommercie.com';"
   ```

3. Faça login de novo (`POST /api/v1/auth/login`) para receber um token com o papel novo.

---

## Testar pelo Swagger ou pelo Postman

A autenticação é por cookie httpOnly — não há token no corpo nem header `Authorization`.

- **Swagger UI:** chame `POST /api/v1/auth/login` em "Try it out". O navegador guarda os cookies e
  os envia nas chamadas seguintes. O botão "Authorize" não consegue gravar um cookie httpOnly.
- **Postman:** o login também basta — o Postman guarda os cookies de `localhost` e os reenvia.
  Para trocar de usuário, apague os cookies em *Cookies → localhost* ou chame o logout.

Fluxo de compra, em ordem:

1. Admin cria categoria e produto (`/api/v1/admin/catalog/...`).
2. Admin define o estoque: `PATCH /api/v1/admin/inventory/{productId}` — o produto nasce com estoque 0.
3. Cliente adiciona ao carrinho, cota o frete (`POST /api/v1/shipping/quote`) e faz o checkout
   (`POST /api/v1/orders`).
4. Cliente cria a preferência de pagamento (`POST /api/v1/payments/{orderId}/preference`) e paga
   no Mercado Pago. O webhook marca o pedido como `PAGO`.
5. Admin gera a etiqueta (`POST /api/v1/admin/orders/{id}/label`) e avança o pedido.

---

## Rodar os testes

Os testes sobem um Postgres real via Testcontainers. Precisam do **Docker rodando**.

```bash
./mvnw clean verify
```

Nenhum teste toca a rede: Mercado Pago e Melhor Envio são substituídos por stubs
(`src/test/java/com/ecommercie/support/`). A configuração dos testes fica em
`src/test/resources/application-test.yml`.

---

## Variáveis de ambiente

Todas vivem no `.env` (fora do git). O modelo é o `.env.example`. **Nenhum segredo é versionado.**

| Variável | Obrigatória | Para quê |
|---|---|---|
| `DB_PASSWORD` | **sim** | senha do Postgres |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` | não | conexão Postgres — **só o perfil `docker` lê**; o local usa `localhost:5432/ecommercie` fixo |
| `JWT_SECRET` | **sim** | assinatura HS256 (mín. 256 bits) |
| `CORS_ORIGIN` | não | origens do front, separadas por vírgula (padrão `http://localhost:4200`) |
| `MP_ACCESS_TOKEN` `MP_NOTIFICATION_URL` | **sim** | Mercado Pago |
| `ME_TOKEN` `ME_USER_AGENT` `SHIPPING_ORIGIN_CEP` | **sim** | Melhor Envio |
| `ME_BASE_URL` | não | padrão: sandbox do Melhor Envio |
| `MAIL_HOST` `MAIL_PORT` `MAIL_USER` `MAIL_PASS` `MAIL_AUTH` `MAIL_STARTTLS` `MAIL_FROM` | não | SMTP transacional (padrão: `localhost:1025`, sem auth) |
| `FISCAL_ENABLED` `FISCAL_TIPO` `FOCUSNFE_*` | não | módulo fiscal (padrão: desligado) |

### E-mail em desenvolvimento

O padrão aponta para um SMTP em `localhost:1025`. O Mailpit captura os e-mails e mostra numa
interface web em <http://localhost:8025>:

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
# nas próximas vezes:
docker start mailpit
```

### Webhooks em desenvolvimento

Mercado Pago e Melhor Envio precisam alcançar a sua máquina. Use um túnel (ngrok, cloudflared):

```bash
ngrok http 8080
# use a URL pública em MP_NOTIFICATION_URL:
#   https://SEU-TUNEL/api/v1/webhooks/mercadopago
```

No sandbox do Mercado Pago, **pague com a conta "Comprador" de teste**, nunca com a do vendedor
(pagar com o vendedor não dispara o webhook).

---

## Dependências do cliente (antes do go-live)

Itens que **não são código**: só o lojista ou o contador dele fornece. Cada área fica bloqueada
até o item correspondente chegar. Detalhes em [`../docs/system-design.md` §9](../docs/system-design.md).

### Fiscal / NF-e — o bloco mais crítico (§9.1)

O módulo fiscal nasce **desligado** (`FISCAL_ENABLED=false`) e a loja funciona sem ele. Para ligar:

1. **Habilitação do MEI para emitir NF-e** — em muitos estados exige Inscrição Estadual e
   credenciamento. **É o item mais demorado e pré-requisito de todo o resto.**
2. **Certificado digital A1** (`.pfx` + senha) — o provedor assina o XML com ele.
   *(Exigido para NF-e, não para DC-e.)*
3. **Escolha do provedor** (Focus NFe, NFe.io, PlugNotas, eNotas) — define o custo por nota.
4. **Conta + token do provedor** (homologação e produção) → `FOCUSNFE_TOKEN`, `FOCUSNFE_BASE_URL`.
5. **Regime tributário e CSOSN/CST** — o contador confirma.
6. **CFOP padrão de venda** — ex.: 5102 (dentro do estado), 6102 (fora).
7. **Dados fiscais por produto**: NCM, origem, unidade tributável, CEST. Sem NCM correto a SEFAZ rejeita.
8. **Dados cadastrais da empresa**: razão social, CNPJ, IE, endereço fiscal, CNAE.
9. **Numeração e série inicial da NF-e** — sequencial; não pode colidir com notas já emitidas.
10. **Política de cancelamento / carta de correção** — prazo legal e quem aciona.

### Pagamento — Mercado Pago (§9.2)

- Conta MP de produção **verificada**.
- `Access Token` de produção → `MP_ACCESS_TOKEN`.
- Métodos habilitados (PIX / cartão / boleto) e prazos de expiração.

### Frete — Melhor Envio (§9.3)

- Conta ME **com saldo** e **Correios ativos** no painel.
- Token da API de produção → `ME_TOKEN`; `ME_BASE_URL` apontando para produção.
- CEP de origem (despacho) → `SHIPPING_ORIGIN_CEP`.
- E-mail de contato do lojista no `ME_USER_AGENT` (exigência da API do ME).

### Identidade da loja e e-mail (§9.4)

- Nome da loja, e-mail de contato, regras de frete grátis.
- Conta SMTP (Brevo/Resend) + credenciais → `MAIL_*`.
- **Domínio verificado** no provedor SMTP (sem isso os e-mails caem em spam).
- Domínio do front → `CORS_ORIGIN`.

---

## Pendências conhecidas

- **Perfil local sem migrations:** o perfil padrão usa `ddl-auto: update` com o Flyway desligado.
  O schema de verdade é o das migrations (perfil `docker` e testes); uma mudança de entidade sem
  migration funciona localmente e quebra no Docker.
- **Assinatura do webhook do Mercado Pago:** a validação do cabeçalho `x-signature` ainda não está
  implementada. A idempotência está (tabela `webhook_event`), e o pagamento é sempre reconsultado na
  API do MP antes de marcar o pedido como pago.
- **Transições de pedido espalhadas:** o `MelhorEnvioClient.buyLabel` (um adapter HTTP) muda o status
  do pedido para `EM_SEPARACAO`/`ENVIADO`, e o webhook de rastreio (`ShipmentTrackingService`) marca
  `ENTREGUE` direto na entidade. Por isso o e-mail de "enviado" é registrado em dois lugares
  (`OrderService.enviar` e `ShippingService.gerarEtiqueta`) e o de "entregue" também
  (`OrderService.entregar` e `ShipmentTrackingService`). O ideal é concentrar cada transição num
  único método do `OrderService`, que já registra o e-mail, chamado pelos dois caminhos — e o
  client só falar com a API do Melhor Envio.

---

## Estrutura

```
src/main/java/com/ecommercie/
├── security/      auth, JWT, cookies, SecurityConfig
├── catalogo/      produtos, categorias, imagens
├── estoque/       reserva e baixa (concorrência com lock pessimista)
├── carrinho/      carrinho do cliente
├── pedido/        checkout, máquina de estados, job de expiração, admin de pedidos/clientes
├── outbox/        efeitos externos fora da transação de negócio
├── mercado_pago/  gateway de pagamento + webhook idempotente
├── melhor_envio/  cotação, etiqueta, rastreio + webhook
├── devolucao/     devoluções e estorno
├── fiscal/        NF-e / DC-e (desligado por padrão)
├── config/        OpenAPI, storage
└── shared/        ApiResponse, BaseEntity, GlobalExceptionHandler
```

## Convenções

- Toda resposta de negócio usa o envelope `{ success, message, data }`
  ([`api-response.md`](../docs/patterns/api-response.md)). Webhooks são a única exceção: `200 OK` puro.
- Erros nunca são montados no controller — vão pelo `GlobalExceptionHandler`.
- DTO de resposta sempre, nunca entidade JPA. Conversão por `from()` manual (sem MapStruct).
- Efeito externo (e-mail, NF-e) **nunca** dentro de transação de negócio — vai pelo outbox.
- Preço é **sempre** recalculado no servidor a partir do catálogo.
