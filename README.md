# Feedback Platform

Plataforma serverless para coleta e análise de feedbacks de aulas, desenvolvida para o Tech Challenge da Fase 4 (FIAP). Estudantes enviam avaliações via HTTP; administradores recebem alertas imediatos para feedbacks críticos e um relatório semanal consolidado.

A solução é implementada em **Java 21** sobre **Azure Functions (Runtime 4)**, com persistência em **Azure Cosmos DB**, mensageria em **Azure Storage Queue** e envio de e-mails via **Azure Communication Services (ACS) Email**. Uma API **Spring Boot** equivalente permite execução e depuração locais dos mesmos endpoints.

---

## Sumário

1. [Requisitos do desafio e cobertura](#1-requisitos-do-desafio-e-cobertura)
2. [Arquitetura](#2-arquitetura)
3. [Modelo de nuvem e componentes](#3-modelo-de-nuvem-e-componentes)
4. [Contrato da API](#4-contrato-da-api)
5. [Documentação das Functions](#5-documentação-das-functions)
6. [Configuração](#6-configuração)
7. [Execução local](#7-execução-local)
8. [Build e deploy](#8-build-e-deploy)
9. [Monitoramento e alertas](#9-monitoramento-e-alertas)
10. [Segurança e governança de acesso](#10-segurança-e-governança-de-acesso)
11. [Limitações conhecidas](#11-limitações-conhecidas)
12. [Estrutura do código](#12-estrutura-do-código)
13. [Autores](#13-autores)

---

## 1. Requisitos do desafio e cobertura

| Requisito | Implementação |
| --- | --- |
| Aplicação serverless em nuvem | Azure Functions (Linux, Java 21, Runtime 4) |
| Mínimo de 2 funções, com responsabilidade única | 5 functions, cada uma com um único gatilho e uma única responsabilidade (seção 5) |
| Banco de dados e componentes de suporte | Azure Cosmos DB (NoSQL), Azure Storage (fila e estado do runtime), ACS Email |
| Notificação automática para itens críticos | `notificacao-urgencia` envia e-mail ao administrador para urgência `ALTA` |
| Relatório semanal com média de avaliações | `relatorio-semanal` (Timer Trigger, segunda-feira 09:00 UTC) |
| Segurança e governança de acesso | Segredos em App Settings, `DefaultAzureCredential` (seção 10) |
| Deploy automatizado | Maven Azure Functions Plugin (seção 8) |
| Aplicação monitorada | Seção 9 |

---

## 2. Arquitetura

![Diagrama de arquitetura da Feedback Platform](docs/arquitetura.svg)

### Fluxo de um feedback crítico

1. O cliente envia `POST /api/avaliacao` com `urgencia = ALTA`.
2. `avaliacao-post` valida o payload, persiste o documento no Cosmos DB e publica uma mensagem em `urgency-notifications`.
3. `notificacao-urgencia` é acionada pela mensagem e envia ao administrador (`AZURE_NOTIFICATION_EMAIL`) um e-mail com **descrição**, **urgência** e **data de envio**.
4. Em caso de falha no envio, a exceção é registrada e propagada, permitindo que o runtime reprocesse a mensagem conforme a política de retry de filas do Azure Functions.

### Desacoplamento

O recebimento do feedback e o envio de notificação são separados por uma fila. Isso evita que a latência ou a indisponibilidade do serviço de e-mail afete a resposta ao cliente e permite reprocessamento independente.

---

## 3. Modelo de nuvem e componentes

**Modelo:** Function-as-a-Service (FaaS) sobre Azure, com serviços gerenciados (PaaS) para dados e mensageria.

| Componente | Serviço Azure | Responsabilidade |
| --- | --- | --- |
| Computação | Azure Functions (Runtime 4, Java 21, Linux) | Execução orientada a eventos (HTTP, fila, timer) |
| Persistência | Azure Cosmos DB (NoSQL) | Armazenamento dos feedbacks |
| Mensageria | Azure Storage Queue (`urgency-notifications`) | Desacoplamento entre recebimento e notificação |
| Estado do runtime | Azure Storage (`AzureWebJobsStorage`) | Gatilhos de fila e timer, controle de execução |
| E-mail | Azure Communication Services Email | Alertas e relatório semanal |

### Parâmetros de provisionamento (`pom.xml`)

| Parâmetro | Valor |
| --- | --- |
| Function App | `feedback-platform-serverless` |
| Resource group | `rg-feedback-platform` |
| Região | `westeurope` |
| Sistema operacional | Linux |
| Java | 21 |
| `FUNCTIONS_EXTENSION_VERSION` | `~4` |
| Extension bundle (`host.json`) | `Microsoft.Azure.Functions.ExtensionBundle` `[4.*, 5.0.0)` |

> **A preencher:** plano de hospedagem efetivo (Consumption, Flex Consumption ou Premium) e a justificativa da escolha. O `pom.xml` não define `pricingTier`; confirme no portal o plano criado.

---

## 4. Contrato da API

As rotas são idênticas na API Spring (`http://localhost:8080/avaliacao`) e nas Azure Functions (`/api/avaliacao`).

### `POST /avaliacao`

Cria um feedback.

**Corpo da requisição**

| Campo | Tipo | Obrigatório | Regras |
| --- | --- | --- | --- |
| `descricao` | string | Sim | Texto livre do feedback |
| `nota` | inteiro | Sim | Entre 0 e 10 (inclusive) |
| `urgencia` | string | Sim | `BAIXA`, `MEDIA` ou `ALTA`. Case-insensitive; também aceita `Média` |

```bash
curl -X POST http://localhost:8080/avaliacao \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Atendimento muito bom",
    "nota": 9,
    "urgencia": "Alta"
  }'
```

**Mensagens de validação** (Bean Validation): `Descrição é obrigatória`, `Nota é obrigatória`, `Nota mínima é 0`, `Nota máxima é 10`, `Urgência é obrigatória`.

**Normalização de `urgencia`:** o valor é aparado, sem acentos e convertido para maiúsculas antes de mapear para o enum. Assim, `Média`, `media` e `MEDIA` resultam em `MEDIA`.

**Respostas**

| Status | Condição | Corpo |
| --- | --- | --- |
| `201 Created` | Feedback persistido | Documento criado (abaixo) |
| `400 Bad Request` | Body ausente ou violação de validação | Functions: `{"error": "<primeira violação>"}`. API Spring: corpo de erro padrão do Spring Boot |
| `500 Internal Server Error` | Falha de persistência ou erro não tratado | Functions: `{"error": "..."}` |

```json
{
  "id": "6f1c2d9e-3b7a-4c55-9a1e-0d2f5e8b7c31",
  "descricao": "Atendimento muito bom",
  "nota": 9,
  "urgencia": "ALTA",
  "dataEnvio": "2026-10-26T14:03:11.123"
}
```

- `id` é um UUID gerado pelo serviço.
- `urgencia` é serializada pelo nome do enum (`BAIXA`, `MEDIA`, `ALTA`), sem acento.
- `dataEnvio` é ISO-8601 em **UTC, sem offset**, gerada no servidor.

> Os formatos de erro das Functions e da API Spring diferem; ver [Limitações conhecidas](#11-limitações-conhecidas).

### Documentação OpenAPI (API Spring)

A API Spring inclui `springdoc-openapi` 2.6.0. Com a API em execução, a especificação fica em `/v3/api-docs` e a interface Swagger UI em `/swagger-ui.html`.

### `GET /avaliacao`

Lista os feedbacks persistidos.

### `GET /avaliacao/health`

Retorna o status da API Spring. Nas Functions, o equivalente é `GET /api/health`.

---

## 5. Documentação das Functions

Implementadas na classe `FeedbackFunction`. Cada function tem uma única responsabilidade.

| FunctionName | Gatilho | Rota / origem | Responsabilidade |
| --- | --- | --- | --- |
| `avaliacao-post` | `HttpTrigger` (POST) | `/api/avaliacao` | Validar, persistir e, se `ALTA`, enfileirar notificação |
| `avaliacao-list` | `HttpTrigger` (GET) | `/api/avaliacao` | Listar feedbacks |
| `health` | `HttpTrigger` (GET) | `/api/health` | Verificação de saúde |
| `notificacao-urgencia` | `QueueTrigger` | fila `urgency-notifications` | Enviar e-mail de alerta ao administrador |
| `relatorio-semanal` | `TimerTrigger` | `0 0 9 * * MON` (UTC) | Gerar e enviar o relatório semanal |

### `notificacao-urgencia`

- **Entrada:** mensagem da fila com os dados do feedback.
- **Conteúdo do e-mail:** descrição, urgência e data de envio.
- **Falhas:** a exceção é registrada e propagada para que o runtime reprocese a mensagem. O `host.json` não sobrescreve a política de filas, então valem os padrões do runtime (por padrão, `maxDequeueCount = 5`); esgotadas as tentativas, a mensagem vai para a fila `urgency-notifications-poison`.
- **Pré-condição:** `AZURE_COMMUNICATION_CONNECTION_STRING`, `AZURE_EMAIL_SENDER` e `AZURE_NOTIFICATION_EMAIL` precisam estar definidas; caso contrário, a function lança `IllegalStateException`.

### `relatorio-semanal`

- **Agendamento:** expressão NCRONTAB `0 0 9 * * MON`, toda segunda-feira às 09:00 UTC.
- **Janela de dados:** semana completa anterior (segunda a domingo).
- **Conteúdo do relatório:**
  - lista de feedbacks (descrição, urgência, data de envio);
  - média das notas;
  - quantidade de avaliações por dia;
  - quantidade de avaliações por urgência.
- **Destinatário:** `AZURE_NOTIFICATION_EMAIL`.
- **Logs:** registram apenas período, total de avaliações e média. Descrições de feedbacks não são logadas.

### Conexão de armazenamento

O Timer Trigger e a fila usam a conexão `AzureWebJobsStorage`, declarada explicitamente nos bindings. Localmente ela vale `UseDevelopmentStorage=true` (`local.settings.json`), o que exige o **Azurite** em execução. Na Function App publicada, deve apontar para uma conta Azure Storage válida.

---

## 6. Configuração

### Variáveis de ambiente

| Variável | Obrigatória | Descrição | Padrão |
| --- | --- | --- | --- |
| `AZURE_COSMOS_ENDPOINT` | Recomendada | Endpoint da conta Cosmos DB | `https://cosmo-db-feedback.documents.azure.com:443/` (definido em `application.yml`) |
| `AZURE_COSMOS_DATABASE` | Não | Nome do banco | `feedback-db` |
| `AZURE_COSMOS_CONTAINER` | Não | Nome do container | `feedback-container` |
| `AZURE_COSMOS_KEY` | Não | Chave da conta. Se ausente, usa `DefaultAzureCredential` | — |
| `AZURE_COMMUNICATION_CONNECTION_STRING` | Sim (e-mails) | Connection string do ACS Email | — |
| `AZURE_EMAIL_SENDER` | Sim (e-mails) | Remetente do domínio provisionado no ACS | — |
| `AZURE_NOTIFICATION_EMAIL` | Sim (alertas e relatório) | E-mail do administrador destinatário | — |
| `AzureWebJobsStorage` | Sim | Conexão do Storage usada por fila e timer | `UseDevelopmentStorage=true` (local) |

### Pré-requisitos de infraestrutura

- Banco e container já criados no Cosmos DB, com partition key definida. O código usa `createItem` e não declara a chave; informe aqui o path utilizado.
- Domínio de e-mail provisionado e verificado no ACS.
- Conta Azure Storage (fila `urgency-notifications` e `AzureWebJobsStorage`).

### Exemplo (Linux/macOS)

```bash
export AZURE_COSMOS_ENDPOINT="https://<sua-conta>.documents.azure.com:443/"
export AZURE_COSMOS_DATABASE="feedback-db"
export AZURE_COSMOS_CONTAINER="feedback-container"
export AZURE_COSMOS_KEY="<sua-chave>"   # opcional; sem ela, usa DefaultAzureCredential
export AZURE_COMMUNICATION_CONNECTION_STRING="<connection-string>"
export AZURE_EMAIL_SENDER="<remetente-do-dominio-provisionado>"
export AZURE_NOTIFICATION_EMAIL="<email-destinatario>"
```

---

## 7. Execução local

### Pré-requisitos

- JDK 21 e Maven
- Azure Functions Core Tools v4
- Azurite (para fila e timer)

### API Spring Boot

```bash
mvn spring-boot:run
```

Disponível em `http://localhost:8080`, com endpoints em `/avaliacao`.

### Azure Functions

Com o Azurite em execução e `AzureWebJobsStorage` configurado em `local.settings.json`:

```bash
mvn azure-functions:run
```

Disponível em `http://localhost:7071/api/...`.

> **Atenção:** as Functions inicializam o contexto Spring completo (`SpringApplication.run`) e, como `spring-boot-starter-web` está no classpath, ele sobe um Tomcat na porta `8080`. Não execute `mvn spring-boot:run` e `mvn azure-functions:run` ao mesmo tempo na mesma máquina, ou ocorrerá conflito de porta.

### Testes e build

```bash
mvn clean verify
```

---

## 8. Build e deploy

```bash
mvn clean package
mvn azure-functions:deploy
```

O plugin Maven em `pom.xml` está configurado para Azure Functions em **Linux**, **Java 21** e **Runtime 4**. Nome do aplicativo, grupo de recursos e região também são definidos nesse arquivo.

**Pré-requisitos do deploy**

1. Autenticação Azure (`az login`).
2. App Settings da Function App configuradas conforme a seção 6. O `pom.xml` define apenas `FUNCTIONS_EXTENSION_VERSION=~4`; as demais (Cosmos, ACS, `AzureWebJobsStorage`) são configuradas manualmente.

> **Atenção:** `.azure/default/deployment.targets.xml` aponta para um alvo do tipo **Azure App Service**, que é diferente de Azure Functions. Ao publicar pelo Azure Toolkit, confirme ou atualize o alvo salvo. Os comandos Maven acima usam o plugin de Azure Functions.

> **A preencher:** pipeline de CI/CD (por exemplo, GitHub Actions), se houver, com gatilhos, etapas e segredos utilizados.

---

## 9. Monitoramento e alertas

**Estado atual do repositório:** o `host.json` define apenas o extension bundle e não customiza logging, amostragem ou política de filas. Os logs são emitidos via `context.getLogger()` nas functions.

> **A preencher:** esta seção é um critério de avaliação do desafio. Documente a configuração real do ambiente. Sugestão de estrutura:

- **Telemetria:** recurso (por exemplo, Application Insights) vinculado à Function App; como foi habilitado.
- **Métricas acompanhadas:** execuções, falhas, duração, mensagens na fila, mensagens em poison queue.
- **Alertas:** regras configuradas (condição, limiar, grupo de ação, destinatários).
- **Logs:** consultas úteis (por exemplo, falhas de `notificacao-urgencia`).
- **Privacidade:** descrições de feedbacks não são registradas em log nas rotinas de relatório.

---

## 10. Segurança e governança de acesso

- **Segredos fora do código:** `AZURE_COMMUNICATION_CONNECTION_STRING` e demais credenciais são configuradas como App Settings (segredos) da Function App, nunca versionadas.
- **Autenticação no Cosmos DB:** sem `AZURE_COSMOS_KEY`, a aplicação usa `DefaultAzureCredential`, permitindo Managed Identity com permissões mínimas necessárias. O uso de chave deve ser restrito a desenvolvimento local.
- **Validação de entrada:** nota entre 0 e 10, urgência restrita ao conjunto `BAIXA`/`MEDIA`/`ALTA`, campos obrigatórios validados no DTO.
- **Minimização de dados em logs:** logs do relatório não contêm o conteúdo dos feedbacks.

- **Autorização das rotas HTTP:** as três functions HTTP (`avaliacao-post`, `avaliacao-list`, `health`) usam `AuthorizationLevel.ANONYMOUS`. Isso inclui a listagem de feedbacks (ver [Limitações conhecidas](#11-limitações-conhecidas)).

> **A preencher:** papéis RBAC atribuídos à identidade da Function App e restrições de rede (se houver).

---

## 11. Limitações conhecidas

| Área | Limitação | Melhoria sugerida |
| --- | --- | --- |
| Inicialização | O contexto Spring Web (Tomcat na porta 8080) é iniciado dentro do worker das Functions, aumentando o cold start | Definir `spring.main.web-application-type=none` para o perfil das Functions |
| Tratamento de erros | Nas Functions, JSON malformado ou `urgencia` inválida resulta em `500`; na API Spring resulta em `400`. Os corpos de erro também diferem | Capturar `JsonProcessingException` e retornar `400`; padronizar o corpo de erro com `@RestControllerAdvice` |
| Exposição de erros | Respostas `500` incluem `ex.getMessage()` | Retornar mensagem genérica e manter o detalhe apenas no log |
| Autorização | Todas as rotas HTTP são `ANONYMOUS`, inclusive a listagem de feedbacks | Usar `AuthorizationLevel.FUNCTION` ou camada de autenticação (Easy Auth, API Management) nas rotas administrativas |
| Consistência | Gravação no Cosmos DB e publicação na fila não são atômicas; se a fila falhar após a gravação, o feedback fica salvo sem alerta | Padrão outbox ou Change Feed do Cosmos DB como origem do evento |
| Consulta | `findAll()` executa `SELECT * FROM c` sem paginação; o relatório filtra a semana em memória | Filtrar por `dataEnvio` na query (`WHERE c.dataEnvio >= @inicio AND c.dataEnvio < @fim`) e paginar a listagem |
| E-mail | O resultado final de `waitForCompletion()` não é inspecionado, e o destinatário é único | Validar o status final do envio; suportar lista de destinatários |
| Health | `/health` apenas retorna `UP` (liveness), sem verificar Cosmos DB ou fila | Adicionar verificação de dependências (readiness) |

---

## 12. Estrutura do código

```text
src/main/java/com/fiap/feedback/
├── config/       # Configuração do cliente Cosmos DB
├── controller/   # Endpoints REST (Spring)
├── dto/          # Contrato de entrada (FeedbackRequest) e relatório (WeeklyFeedbackReport)
├── enums/        # Urgencia (normalização de caixa e acentos)
├── function/     # Triggers do Azure Functions (HTTP, Queue, Timer)
├── model/        # Modelo de feedback
├── repository/   # Persistência no Cosmos DB
└── service/      # Regras de negócio
```

A camada de `service` concentra as regras de negócio e é reutilizada pela API Spring e pelas Functions. As Functions inicializam o contexto Spring em um inicializador estático (`SpringApplication.run`) e obtêm os beans (serviços, `ObjectMapper`, `Validator`) a partir dele, mantendo os triggers restritos a parse, validação e montagem da resposta.

---

## 13. Autores

| Nome | RM |
| --- | --- |
| Thiago Vanzele | RM374158 |
| Luiz Eduardo Gambeti | RM373030 |
| Sandoval Bento da Silva | RM370706 |
