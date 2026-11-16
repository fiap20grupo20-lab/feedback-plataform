# Feedback Platform

Plataforma serverless para coleta e análise de feedbacks de aulas, desenvolvida para o Tech Challenge da Fase 4 (FIAP). Estudantes enviam avaliações via HTTP; administradores recebem alertas imediatos para feedbacks críticos e um relatório semanal consolidado, com a média das avaliações.

A solução roda na **Microsoft Azure**, em **Java 21** sobre **Azure Functions (Runtime 4)**, com persistência em **Azure Cosmos DB**, mensageria em **Azure Storage Queue**, envio de e-mails via **Azure Communication Services (ACS) Email**, monitoramento com **Application Insights** e deploy automatizado por **GitHub Actions**. Uma API **Spring Boot** equivalente permite executar e depurar os mesmos endpoints localmente.

- **Repositório:** https://github.com/fiap20grupo20-lab/feedback-plataform
- **Vídeo de demonstração:** https://drive.google.com/file/d/1B2-a422clTyEZqAU35dJRsj8hwhnEodU/view?usp=sharing
- **URL base em produção:** https://app-261014193432.azurewebsites.net
---

## Sumário

1. [Requisitos do desafio e cobertura](#1-requisitos-do-desafio-e-cobertura)
2. [Arquitetura da solução](#2-arquitetura-da-solução)
3. [Modelo de nuvem e componentes](#3-modelo-de-nuvem-e-componentes)
4. [Endpoints e respostas da API](#4-endpoints-e-respostas-da-api)
5. [Documentação das Functions serverless](#5-documentação-das-functions-serverless)
6. [Conteúdo dos e-mails](#6-conteúdo-dos-e-mails)
7. [Modelo de dados](#7-modelo-de-dados)
8. [Configuração](#8-configuração)
9. [Deploy](#9-deploy)
10. [Monitoramento e alertas](#10-monitoramento-e-alertas)
11. [Segurança e governança de acesso](#11-segurança-e-governança-de-acesso)
12. [Como validar o funcionamento](#12-como-validar-o-funcionamento)
13. [Execução local](#13-execução-local)
14. [Limitações conhecidas](#14-limitações-conhecidas)
15. [Estrutura do código](#15-estrutura-do-código)
16. [Autores](#16-autores)
---

## 1. Requisitos do desafio e cobertura

### Requisitos e regras da aplicação

| Requisito | Como foi atendido | Seção |
| --- | --- | --- |
| Ambiente de nuvem configurado, com segurança e governança de acesso | Azure, Resource Group dedicado, RBAC, Somente HTTPS, segredos em variáveis de ambiente | 3 e 11 |
| Componentes de suporte (banco de dados etc.) | Cosmos DB, Storage Queue, Azure Storage, ACS Email | 3 e 8 |
| Deploy automatizado dos componentes atualizáveis | GitHub Actions publica as functions a cada push na `main` | 9 |
| Aplicação monitorada | Application Insights com Log Analytics | 10 |
| Notificações automáticas aos administradores para problemas críticos | Alerta de severidade 0 (Crítico) com grupo de ações por e-mail; e-mail de urgência alta | 10 e 6 |
| Relatório semanal com média de avaliações | `relatorio-semanal` (Timer Trigger), segunda-feira 09:00 UTC | 5 e 6 |
| Obrigatoriamente serverless | Azure Functions, plano Consumption | 3 |
| Obrigatoriamente em nuvem | Todos os componentes executam na Azure (Central US) | 3 |
| Mínimo de 2 functions, com Responsabilidade Única | 5 functions, cada uma com um gatilho e uma responsabilidade | 5 |

### Critérios de avaliação e onde encontrar

| Critério | Seção |
| --- | --- |
| Explicação do modelo de cloud e dos componentes | 3 |
| Funcionamento correto da aplicação | 4, 5 e 12 |
| Qualidade do código, com documentação | 5, 14 e 15 |
| Arquitetura da solução | 2 |
| Instruções de deploy | 9 |
| Configuração do monitoramento | 10 |
| Documentação das funções criadas | 5 |
| Configuração do ambiente de nuvem e segurança | 3, 8 e 11 |
 
---

## 2. Arquitetura da solução

![Diagrama de arquitetura da Feedback Platform](docs/arquitetura.svg)

Representação em texto do mesmo fluxo:

```text
                    +---------------------------+
   Cliente  ------> |  avaliacao-post  (HTTP)   | ------> Cosmos DB (feedbacks)
 POST /api/avaliacao|  valida, salva e, se ALTA, |
                    |  enfileira                |
                    +-------------+-------------+
                                  | urgencia = ALTA
                                  v
                    +---------------------------+
                    |  Storage Queue            |
                    |  urgency-notifications    |
                    +-------------+-------------+
                                  v
                    +---------------------------+
                    | notificacao-urgencia      | ------> ACS Email ------> Administrador
                    | (Queue Trigger)           |
                    +---------------------------+
 
   Timer (segunda 09:00 UTC) --> relatorio-semanal --> Cosmos DB --> ACS Email --> Administrador
 
   Cliente --> avaliacao-list (GET)   |   Cliente --> health (GET)
 
   Todas as functions --> Application Insights --> Regra de alerta --> Grupo de ações --> E-mail
```

### Fluxo de um feedback

1. O cliente envia `POST /api/avaliacao` com `descricao`, `nota` e `urgencia`.
2. `avaliacao-post` valida o payload e persiste o documento no Cosmos DB. A resposta `201 Created` devolve o documento criado.
3. Se `urgencia = ALTA`, a function também publica o feedback em formato JSON na fila `urgency-notifications`. A resposta ao cliente não espera o envio do e-mail.
4. `notificacao-urgencia` é acionada pela mensagem e envia ao administrador um e-mail com **descrição**, **urgência** e **data de envio**.
5. Em caso de falha no envio, a exceção é registrada e propagada, e o runtime reprocessa a mensagem conforme a política de retry de filas do Azure Functions (padrão: 5 tentativas; depois, a mensagem vai para a fila `urgency-notifications-poison`).
### Fluxo do relatório semanal

1. Toda segunda-feira às 09:00 UTC (06:00 no horário de Brasília), o Timer Trigger aciona `relatorio-semanal`.
2. A function calcula a semana anterior completa (segunda a domingo), consulta o Cosmos DB e filtra os feedbacks do período.
3. O relatório traz a lista de feedbacks, a **média das notas**, a **quantidade de avaliações por dia** e a **quantidade por urgência**, e é enviado por e-mail ao administrador.
### Decisões de projeto

- **Desacoplamento por fila:** receber o feedback e notificar são responsabilidades separadas. Uma falha ou lentidão no serviço de e-mail não afeta a resposta ao estudante e permite reprocessamento independente.
- **Camadas:** os triggers das functions apenas leem a entrada, validam e montam a resposta. A regra de negócio fica em `service`, o acesso a dados em `repository` e o envio de e-mail em serviços dedicados.
---

## 3. Modelo de nuvem e componentes

**Modelo escolhido:** nuvem pública (Microsoft Azure), no modelo **Function-as-a-Service (FaaS)**, com serviços **PaaS** gerenciados para dados, mensageria, e-mail e monitoramento. Não há servidores para administrar: o código só executa quando é acionado (requisição HTTP, mensagem na fila ou horário agendado) e o custo é proporcional ao uso.

### Componentes

| Componente | Serviço Azure | Responsabilidade |
| --- | --- | --- |
| Computação | Azure Functions (Runtime 4, Java 21, Windows, Consumption) | Execução orientada a eventos (HTTP, fila, timer) |
| Persistência | Azure Cosmos DB (NoSQL) | Armazenamento dos feedbacks |
| Mensageria | Azure Storage Queue (`urgency-notifications`) | Desacoplamento entre recebimento e notificação |
| Estado do runtime | Azure Storage (`AzureWebJobsStorage`) | Gatilhos de fila e timer, controle de execução |
| E-mail | Azure Communication Services Email | Alertas de urgência e relatório semanal |
| Monitoramento | Application Insights + Log Analytics | Telemetria, logs e consultas |
| Alertas | Azure Monitor (regra de alerta + grupo de ações) | Notificação dos administradores em caso de falhas |
| Deploy | GitHub Actions | Build, testes e publicação das functions |

### Ambiente provisionado na Azure

| Item | Valor |
| --- | --- |
| Assinatura | Azure subscription 1 |
| Resource Group | `rg-app-261014193432` |
| Região | Central US |
| Function App | `app-261014193432` |
| Plano de hospedagem | `asp-app-261014193432` (Consumption, SKU Y1) |
| Sistema operacional | Windows |
| Java | 21 |
| `FUNCTIONS_EXTENSION_VERSION` | `~4` |
| Extension bundle (`host.json`) | `Microsoft.Azure.Functions.ExtensionBundle` `[4.*, 5.0.0)` |
| Application Insights | `app-261014193432` |
| Grupo de ações | `ag-admins-feedback` |
| Regra de alerta | `alerta-falhas-feedback` |

### Por que serverless no plano Consumption

- **Custo:** o plano cobra apenas pelo tempo de execução e escala a zero sem tráfego. O volume de feedbacks é baixo e imprevisível, e o projeto usa créditos de cloud limitados.
- **Operação:** não há servidor, sistema operacional ou escala para gerenciar.
- **Aderência ao problema:** o sistema é inteiramente orientado a eventos (requisição, mensagem, horário).
- **Contrapartida:** o *cold start* aumenta a latência da primeira requisição após um período ocioso. É aceitável neste cenário.
---

## 4. Endpoints e respostas da API

**URL base (produção):** `https://app-261014193432.azurewebsites.net`  
**URL base (local):** `http://localhost:7071` (Functions) ou `http://localhost:8080` (API Spring, sem o prefixo `/api`).

Todos os endpoints HTTP usam `AuthorizationLevel.ANONYMOUS` (não exigem chave) e trafegam JSON. A plataforma aceita somente HTTPS.

| Método | Rota | Function | Descrição |
| --- | --- | --- | --- |
| `POST` | `/api/avaliacao` | `avaliacao-post` | Cria um feedback |
| `GET` | `/api/avaliacao` | `avaliacao-list` | Lista os feedbacks |
| `GET` | `/api/health` | `health` | Verifica se a aplicação está no ar |

> **Observação sobre o enunciado:** a referência do desafio descreve o endpoint de entrada como `POST /avaliação` com `descricao` e `nota`. A rota foi implementada sem acento (`/avaliacao`) para evitar problemas de codificação em URLs. O campo `urgencia` foi acrescentado ao corpo porque é necessário para decidir o envio da notificação e para compor o e-mail e o relatório.

### `POST /api/avaliacao`

Cria um feedback. Cabeçalho obrigatório: `Content-Type: application/json`.

**Corpo da requisição**

| Campo | Tipo | Obrigatório | Regras |
| --- | --- | --- | --- |
| `descricao` | string | Sim | Texto livre; não pode ser vazio |
| `nota` | inteiro | Sim | De 0 a 10 (inclusive) |
| `urgencia` | string | Sim | `BAIXA`, `MEDIA` ou `ALTA`. Sem distinção de maiúsculas e minúsculas; aceita também `Média` (acentos são removidos) |

**Exemplo de requisição**

```bash
curl -X POST https://app-261014193432.azurewebsites.net/api/avaliacao \
  -H "Content-Type: application/json" \
  -d '{"descricao":"Sistema fora do ar","nota":2,"urgencia":"ALTA"}'
```

**Respostas**

| Status | Quando ocorre | Corpo |
| --- | --- | --- |
| `201 Created` | Feedback validado e persistido | Documento criado |
| `400 Bad Request` | Corpo ausente ou violação das regras de validação | `{"error": "<mensagem>"}` |
| `500 Internal Server Error` | JSON malformado, `urgencia` fora do conjunto permitido, `nota` não numérica ou falha ao gravar no Cosmos DB / fila | `{"error": "Falha ao processar o feedback: <detalhe>"}` |

**`201 Created`** (`Content-Type: application/json`)

```json
{
  "id": "6f1c2d9e-3b7a-4c55-9a1e-0d2f5e8b7c31",
  "descricao": "Sistema fora do ar",
  "nota": 2,
  "urgencia": "ALTA",
  "dataEnvio": "2026-11-15T16:22:33.123"
}
```

- `id` é um UUID gerado pelo serviço.
- `urgencia` é devolvida pelo nome do enum (`BAIXA`, `MEDIA`, `ALTA`), sem acento.
- `dataEnvio` é ISO-8601 em **UTC, sem offset**, gerada no servidor.
  **`400 Bad Request`**: mensagens possíveis

| Situação | Corpo |
| --- | --- |
| `descricao` ausente ou vazia | `{"error": "Descrição é obrigatória"}` |
| `nota` ausente | `{"error": "Nota é obrigatória"}` |
| `nota` menor que 0 | `{"error": "Nota mínima é 0"}` |
| `nota` maior que 10 | `{"error": "Nota máxima é 10"}` |
| `urgencia` ausente | `{"error": "Urgência é obrigatória"}` |
| Corpo da requisição ausente | `{"error": "Body obrigatório"}` |

Se houver mais de uma violação, a resposta traz apenas a primeira encontrada.

**`500 Internal Server Error`**

```json
{ "error": "Falha ao processar o feedback: <detalhe do erro>" }
```

O erro é registrado no log (nível SEVERE) e fica disponível no Application Insights. Veja a seção 14 sobre a melhoria prevista para transformar JSON malformado e `urgencia` inválida em `400`.

**Efeito colateral:** quando `urgencia = ALTA`, o feedback também é publicado na fila `urgency-notifications`, o que dispara o e-mail de alerta ao administrador.

### `GET /api/avaliacao`

Lista todos os feedbacks persistidos. Não recebe parâmetros.

| Status | Quando ocorre | Corpo |
| --- | --- | --- |
| `200 OK` | Consulta realizada (lista vazia se não houver feedbacks) | Array de feedbacks |
| `500 Internal Server Error` | Falha ao consultar o Cosmos DB | `{"error": "Falha ao consultar feedbacks no Azure Cosmos DB: <detalhe>"}` |

```json
[
  {
    "id": "6f1c2d9e-3b7a-4c55-9a1e-0d2f5e8b7c31",
    "descricao": "Sistema fora do ar",
    "nota": 2,
    "urgencia": "ALTA",
    "dataEnvio": "2026-11-15T16:22:33.123"
  },
  {
    "id": "0a9d4b1c-7e21-4f68-b8d3-5c1e2f7a9b40",
    "descricao": "Ótimo atendimento",
    "nota": 9,
    "urgencia": "BAIXA",
    "dataEnvio": "2026-11-15T16:20:05.870"
  }
]
```

### `GET /api/health`

Verificação de saúde (liveness). Não consulta Cosmos DB nem fila.

| Status | Corpo |
| --- | --- |
| `200 OK` | `{"status": "UP"}` |

### Respostas geradas pela plataforma

Rotas ou métodos não definidos recebem `404 Not Found` do próprio Azure Functions. Se a Function App estiver parada ou em reinício (por exemplo, logo após um deploy), a plataforma pode responder `503 Service Unavailable` por alguns segundos.

### Resumo dos códigos de status

| Código | Significado neste projeto |
| --- | --- |
| `200` | Consulta ou verificação de saúde bem-sucedida |
| `201` | Feedback criado |
| `400` | Dados de entrada inválidos (validação) |
| `404` | Rota ou método inexistente |
| `500` | Erro de processamento ou de infraestrutura (Cosmos DB, fila, JSON malformado) |
 
---

## 5. Documentação das Functions serverless

Implementadas na classe `FeedbackFunction`. Cada function tem **um único gatilho e uma única responsabilidade**.

| FunctionName | Gatilho | Rota / origem | Responsabilidade única |
| --- | --- | --- | --- |
| `avaliacao-post` | `HttpTrigger` (POST) | `/api/avaliacao` | Validar, persistir e, se `ALTA`, enfileirar a notificação |
| `avaliacao-list` | `HttpTrigger` (GET) | `/api/avaliacao` | Listar feedbacks |
| `health` | `HttpTrigger` (GET) | `/api/health` | Verificação de saúde |
| `notificacao-urgencia` | `QueueTrigger` | fila `urgency-notifications` | Enviar e-mail de alerta ao administrador |
| `relatorio-semanal` | `TimerTrigger` | `0 0 9 * * MON` (UTC) | Gerar e enviar o relatório semanal |

### Separação de responsabilidades

| Camada | Classe | Responsabilidade |
| --- | --- | --- |
| Gatilho | `FeedbackFunction` | Receber o evento, validar a entrada e montar a resposta |
| Regra de negócio | `FeedbackService` | Gerar `id` e `dataEnvio` e criar o feedback |
| Acesso a dados | `FeedbackRepository` | Gravar e consultar no Cosmos DB |
| Notificação | `UrgencyNotificationService` | Decidir se o feedback exige alerta (`ALTA`) e compor o e-mail |
| Relatório | `WeeklyFeedbackReportService` | Calcular período, média e contagens da semana anterior |
| Relatório (e-mail) | `WeeklyFeedbackReportEmailService` | Formatar o relatório para envio |
| E-mail | `FeedbackEmailService` e `AzureCommunicationEmailSender` | Validar a configuração e enviar pelo ACS |

### `avaliacao-post`

- **Gatilho:** HTTP POST em `/api/avaliacao`, nível de autorização anônimo.
- **Saída (binding):** Storage Queue `urgency-notifications` (conexão `AzureWebJobsStorage`), usada apenas quando `urgencia = ALTA`.
- **Passos:** lê o corpo, converte para `FeedbackRequest`, valida com Bean Validation, cria o feedback, grava no Cosmos DB e, se `ALTA`, publica o JSON do feedback na fila.
- **Respostas:** `201`, `400` ou `500` (seção 4).
### `avaliacao-list`

- **Gatilho:** HTTP GET em `/api/avaliacao`, nível de autorização anônimo.
- **Passos:** consulta todos os feedbacks do Cosmos DB e devolve a lista.
- **Respostas:** `200` ou `500` (seção 4).
### `health`

- **Gatilho:** HTTP GET em `/api/health`, nível de autorização anônimo.
- **Resposta:** `200` com `{"status":"UP"}`. Usada para verificar rapidamente se a aplicação está no ar.
### `notificacao-urgencia`

- **Gatilho:** mensagem na fila `urgency-notifications` (conexão `AzureWebJobsStorage`).
- **Entrada:** JSON do feedback publicado por `avaliacao-post`.
- **Passos:** converte a mensagem em `Feedback`, confirma que a urgência é `ALTA` e envia o e-mail ao administrador (seção 6).
- **Falhas:** a exceção é registrada e propagada para que o runtime reprocese a mensagem. O `host.json` não sobrescreve a política de filas, então valem os padrões (`maxDequeueCount = 5`); esgotadas as tentativas, a mensagem vai para `urgency-notifications-poison`.
- **Pré-condição:** `AZURE_COMMUNICATION_CONNECTION_STRING`, `AZURE_EMAIL_SENDER` e `AZURE_NOTIFICATION_EMAIL` definidas; caso contrário, lança `IllegalStateException`.
### `relatorio-semanal`

- **Gatilho:** Timer, expressão NCRONTAB `0 0 9 * * MON` (toda segunda-feira, 09:00 UTC, equivalente a 06:00 em Brasília).
- **Janela de dados:** semana completa anterior (segunda a domingo).
- **Passos:** consulta os feedbacks, filtra o período, calcula média e contagens e envia o e-mail (seção 6).
- **Média:** média aritmética das notas da semana; `0.00` quando não há avaliações.
- **Destinatário:** `AZURE_NOTIFICATION_EMAIL`.
- **Falhas:** registradas e propagadas (`IllegalStateException`), o que aparece como exceção no Application Insights. Também falha se algum feedback tiver `dataEnvio` ausente ou inválida.
- **Execução manual (testes e demonstração):** portal Azure → Function App → Funções → `relatorio-semanal` → Código + Teste → Teste/Executar. O portal precisa estar nas origens permitidas de CORS (`https://portal.azure.com`).
- **Logs:** registram apenas período, total de avaliações e média. Descrições de feedbacks não são logadas.
### Conexão de armazenamento

O Timer Trigger e a fila usam a conexão `AzureWebJobsStorage`, declarada explicitamente nos bindings. Localmente vale `UseDevelopmentStorage=true` (exige o Azurite). Na Function App publicada, aponta para a conta Azure Storage criada junto com o app.
 
---

## 6. Conteúdo dos e-mails

Os e-mails são enviados em texto simples pelo Azure Communication Services, ao endereço definido em `AZURE_NOTIFICATION_EMAIL`.

### E-mail de urgência alta

**Assunto:** `Feedback recebido com urgência alta`

```text
Um feedback de urgência alta foi recebido.
 
Descrição: Sistema fora do ar
Urgência: ALTA
Data de envio: 2026-11-15T16:22:33.123
```

Dados incluídos: descrição, urgência e data de envio, conforme o enunciado.

### E-mail do relatório semanal

**Assunto:** `Relatório semanal de feedbacks: <início> a <fim>`

```text
Relatório semanal de feedbacks
Período: 2026-11-02 a 2026-11-08
Total de avaliações: 3
Média das avaliações: 6.33
 
Quantidade por dia:
2026-11-02: 0
2026-11-03: 1
2026-11-04: 2
2026-11-05: 0
2026-11-06: 0
2026-11-07: 0
2026-11-08: 0
 
Quantidade por urgência:
BAIXA: 2
MEDIA: 0
ALTA: 1
 
Feedbacks:
- Descrição: Aula excelente; Urgência: BAIXA; Data de envio: 2026-11-04T14:30:00
- Descrição: Ótimo atendimento; Urgência: BAIXA; Data de envio: 2026-11-04T18:10:42.331
- Descrição: Sistema fora do ar; Urgência: ALTA; Data de envio: 2026-11-03T09:05:12.774
```

(Valores ilustrativos do formato.) Dados incluídos: descrição, urgência, data de envio, média das notas, quantidade de avaliações por dia e quantidade por urgência, conforme o enunciado. Todos os dias da semana aparecem na contagem, inclusive os com zero avaliações.
 
---

## 7. Modelo de dados

Cada feedback é um documento JSON no container do Cosmos DB.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| `id` | string (UUID) | Identificador do documento; também é a chave de partição |
| `descricao` | string | Texto do feedback |
| `nota` | inteiro (0 a 10) | Avaliação |
| `urgencia` | string (`BAIXA`, `MEDIA`, `ALTA`) | Nível de urgência |
| `dataEnvio` | string (ISO-8601, UTC, sem offset) | Momento do recebimento, gerado no servidor |

- **Banco:** `feedback-db` (padrão; configurável por `AZURE_COSMOS_DATABASE`)
- **Container:** `feedback-container` (padrão; configurável por `AZURE_COSMOS_CONTAINER`)
- **Chave de partição:** `/id` (não hierárquica). Como cada feedback recebe um UUID, os documentos se distribuem de forma uniforme entre as partições.
---

## 8. Configuração

### Variáveis de ambiente (App Settings)

Na Azure, são definidas em Function App → Configurações → Variáveis de ambiente.

| Variável | Obrigatória | Descrição | Padrão |
| --- | --- | --- | --- |
| `AZURE_COSMOS_ENDPOINT` | Recomendada | Endpoint da conta Cosmos DB | Definido em `application.yml` |
| `AZURE_COSMOS_DATABASE` | Não | Nome do banco | `feedback-db` |
| `AZURE_COSMOS_CONTAINER` | Não | Nome do container | `feedback-container` |
| `AZURE_COSMOS_KEY` | Não | Chave da conta. Se ausente, usa `DefaultAzureCredential` | n/d |
| `AZURE_COMMUNICATION_CONNECTION_STRING` | Sim (e-mails) | Connection string do ACS Email | n/d |
| `AZURE_EMAIL_SENDER` | Sim (e-mails) | Remetente do domínio provisionado no ACS | n/d |
| `AZURE_NOTIFICATION_EMAIL` | Sim (alertas e relatório) | E-mail do administrador destinatário | n/d |
| `AzureWebJobsStorage` | Sim | Conexão do Storage usada por fila e timer | `UseDevelopmentStorage=true` (local) |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | Sim (monitoramento) | Vincula a Function App ao Application Insights | n/d |
| `FUNCTIONS_EXTENSION_VERSION` | Sim | Versão do runtime | `~4` |

### Pré-requisitos de infraestrutura

- Banco e container criados no Cosmos DB com chave de partição `/id`.
- Domínio de e-mail provisionado e verificado no ACS.
- Conta Azure Storage (fila `urgency-notifications` e `AzureWebJobsStorage`).
- Application Insights vinculado à Function App.
- Origem `https://portal.azure.com` nas origens permitidas de CORS (somente para executar functions pelo portal).
### Exemplo (Linux/macOS, execução local)

```bash
export AZURE_COSMOS_ENDPOINT="https://<sua-conta>.documents.azure.com:443/"
export AZURE_COSMOS_DATABASE="feedback-db"
export AZURE_COSMOS_CONTAINER="feedback-container"
export AZURE_COSMOS_KEY="<sua-chave>"
export AZURE_COMMUNICATION_CONNECTION_STRING="<connection-string>"
export AZURE_EMAIL_SENDER="<remetente-do-dominio-provisionado>"
export AZURE_NOTIFICATION_EMAIL="<email-destinatario>"
```
 
---

## 9. Deploy

### 9.1 Deploy automatizado (GitHub Actions)

O deploy das functions é automatizado pelo workflow `.github/workflows/deploy.yml`, executado a cada **push na branch `main`** e também manualmente (*workflow_dispatch*).

**Etapas**

1. Checkout do código.
2. Configuração do JDK 21 (Temurin) com cache do Maven.
3. `mvn clean package`: compila, executa os testes e empacota as functions em `target/azure-functions/`.
4. Publicação do pacote na Function App `app-261014193432` com a ação `Azure/functions-action`.
   **Workflow**

```yaml
name: Deploy Azure Function
 
on:
  push:
    branches: [ main ]
  workflow_dispatch:
 
env:
  AZURE_FUNCTIONAPP_NAME: app-261014193432
  PACKAGE_PATH: target/azure-functions/feedback-platform-serverless
  JAVA_VERSION: '21'
 
jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
 
      - name: Configurar Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven
 
      - name: Build e testes
        run: mvn clean package
 
      - name: Deploy na Azure Functions
        uses: Azure/functions-action@v1
        with:
          app-name: ${{ env.AZURE_FUNCTIONAPP_NAME }}
          package: ${{ env.PACKAGE_PATH }}
          publish-profile: ${{ secrets.AZURE_FUNCTIONAPP_PUBLISH_PROFILE }}
```

**Segredo necessário:** `AZURE_FUNCTIONAPP_PUBLISH_PROFILE`, com o conteúdo do perfil de publicação da Function App. Cadastro: GitHub → Settings → Secrets and variables → Actions → New repository secret. Para baixar o perfil, use "Obter perfil de publicação" na Visão geral da Function App (a opção "Credenciais de publicação de autenticação básica do SCM" precisa estar habilitada em Configuração → Configurações gerais). O perfil nunca é versionado.

**Como acompanhar:** aba **Actions** do repositório, workflow **Deploy Azure Function**.

### 9.2 Provisionamento inicial (feito uma vez)

1. **Function App**, plano Consumption (Y1) e conta de Storage: criados pelo Azure Toolkit for IntelliJ no Resource Group `rg-app-261014193432`.
2. **Cosmos DB:** conta, banco `feedback-db` e container `feedback-container` com chave de partição `/id`.
3. **Azure Communication Services:** recurso de comunicação e recurso de e-mail com domínio verificado.
4. **Application Insights:** recurso `app-261014193432`, vinculado à Function App.
5. **App Settings:** variáveis da seção 8 configuradas na Function App.
6. **Somente HTTPS** habilitado nas configurações gerais da Function App.
7. **Alerta:** regra e grupo de ações (seção 10).
8. **GitHub:** secret `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` cadastrado.
### 9.3 Build manual

```bash
mvn clean package
```

### 9.4 Sobre o bloco do plugin no `pom.xml`

O `azure-functions-maven-plugin` é usado no build para gerar o pacote em `target/azure-functions/<appName>`. Os valores de `appName` (`feedback-platform-serverless`), `resourceGroup`, `region` e `os` nesse bloco **não correspondem ao ambiente publicado** (seção 3). Eles não são usados pelo deploy via GitHub Actions, que aponta diretamente para a Function App `app-261014193432`. Por isso, **não utilize `mvn azure-functions:deploy`**, que tentaria publicar com esses valores.

> `.azure/default/deployment.targets.xml` aponta para um alvo do tipo Azure App Service. Ao publicar pelo Azure Toolkit, confirme ou atualize o alvo salvo.
 
---

## 10. Monitoramento e alertas

### Telemetria

- **Recurso:** Application Insights `app-261014193432` (Central US), baseado em workspace do Log Analytics.
- **Vínculo:** a Function App envia a telemetria pela variável `APPLICATIONINSIGHTS_CONNECTION_STRING`.
- **O que é coletado:** requisições HTTP, exceções, dependências e logs emitidos pelas functions (`context.getLogger()`).
### Métricas acompanhadas

| Métrica | Onde ver |
| --- | --- |
| Solicitações com falha | Application Insights → Visão geral e Falhas |
| Tempo de resposta do servidor | Application Insights → Visão geral |
| Contagem de requisições | Application Insights → Visão geral e Métricas ao vivo |
| Exceções | Application Insights → Falhas e Logs |
| Invocações por function (sucesso ou falha) | Function App → Funções → função → Monitor |

### Alerta para problemas críticos

| Item | Configuração |
| --- | --- |
| Regra | `alerta-falhas-feedback` |
| Escopo | Application Insights `app-261014193432` |
| Condição | *Failed requests* com limite estático maior que 0 |
| Avaliação | A cada 1 minuto, em janela de 5 minutos |
| Severidade | 0 - Crítico |
| Grupo de ações | `ag-admins-feedback` (nome de exibição `FeedbackAdm`) |
| Notificação | E-mail (`email-admin`) para o administrador |

Quando a regra dispara, o Azure Monitor aciona o grupo de ações e o administrador recebe o e-mail automaticamente. Um alerta só dispara novamente após ser resolvido (uma janela sem falhas).

### Consultas úteis (Application Insights → Logs)

```kusto
// Requisições que falharam
requests
| where success == false
| order by timestamp desc
```

```kusto
// Exceções da notificação de urgência
exceptions
| where operation_Name == "notificacao-urgencia"
| order by timestamp desc
```

```kusto
// Execuções do relatório semanal
requests
| where operation_Name == "relatorio-semanal"
| order by timestamp desc
```

### Como testar o alerta

1. Abra Application Insights → Métricas ao vivo.
2. Envie 3 ou 4 requisições que causam erro 500:
```bash
curl -X POST https://app-261014193432.azurewebsites.net/api/avaliacao \
  -H "Content-Type: application/json" \
  -d '{"descricao":"teste de falha","nota":5,"urgencia":"XYZ"}'
```

3. Confirme as falhas em Application Insights → Falhas.
4. Em até 5 a 10 minutos, Monitor → Alertas mostra o alerta disparado e o administrador recebe o e-mail.
### Privacidade nos logs

Os logs do relatório registram apenas período, total de avaliações e média. Descrições de feedbacks não são registradas.

### Evolução prevista do alerta

Cada execução de function (HTTP, fila ou timer) é registrada como requisição no Application Insights. A regra atual cobre, portanto, qualquer execução que termine em falha. Como evolução, está prevista uma regra adicional baseada em `exceptions` e outra sobre mensagens na fila `urgency-notifications-poison` (seção 14).
 
---

## 11. Segurança e governança de acesso

### Governança de acesso

- Os recursos ficam agrupados no Resource Group `rg-app-261014193432`, e o acesso é controlado por **Azure RBAC (IAM)**.
- Hoje, a conta responsável pela assinatura possui a função **Proprietário**, herdada da assinatura. O código-fonte é compartilhado entre os integrantes pelo repositório GitHub, e o deploy é feito pelo pipeline, sem necessidade de acesso direto ao portal.
- Evolução prevista: conceder aos demais integrantes a função **Colaborador** apenas no escopo do Resource Group (menor privilégio necessário para operar a solução).
### Proteção de dados e segredos

- **Segredos fora do código:** a chave do Cosmos DB (`AZURE_COSMOS_KEY`), a connection string do ACS e demais credenciais ficam em variáveis de ambiente da Function App, nunca versionadas.
- **Segredo do deploy:** o perfil de publicação fica em GitHub Secrets (`AZURE_FUNCTIONAPP_PUBLISH_PROFILE`).
- **Cosmos DB:** o ambiente publicado autentica com a chave da conta. O código também suporta **Managed Identity**: sem `AZURE_COSMOS_KEY`, a aplicação usa `DefaultAzureCredential`. Essa é a evolução recomendada, pois elimina o segredo e permite aplicar RBAC do Cosmos DB com permissões mínimas.
- **Criptografia em repouso:** Cosmos DB e Azure Storage criptografam os dados em repouso por padrão.
- **Transporte:** a Function App está configurada com **Somente HTTPS**; requisições HTTP são redirecionadas para HTTPS.
- **CORS:** apenas `https://portal.azure.com` foi adicionado às origens permitidas, para permitir a execução manual de functions pelo portal.
- **Validação de entrada:** nota entre 0 e 10, urgência restrita a `BAIXA`/`MEDIA`/`ALTA` e campos obrigatórios validados antes de qualquer gravação.
- **Minimização de dados em logs:** os logs do relatório não contêm o conteúdo dos feedbacks.
### Autorização das rotas HTTP

As três functions HTTP usam `AuthorizationLevel.ANONYMOUS`, decisão adotada para simplificar a demonstração do desafio. Isso inclui a listagem de feedbacks. Em produção, a rota `GET /api/avaliacao` deveria exigir chave de função (`AuthorizationLevel.FUNCTION`) ou autenticação (Easy Auth, API Management). Ver seção 14.
 
---

## 12. Como validar o funcionamento

Substitua `BASE` por `https://app-261014193432.azurewebsites.net`.

| # | Teste | Comando | Resultado esperado |
| --- | --- | --- | --- |
| 1 | Saúde | `curl BASE/api/health` | `200` e `{"status":"UP"}` |
| 2 | Feedback normal | `POST /api/avaliacao` com `{"descricao":"Ótimo atendimento","nota":9,"urgencia":"BAIXA"}` | `201` e documento criado |
| 3 | Validação | `POST /api/avaliacao` com `nota` 15 | `400` e `{"error":"Nota máxima é 10"}` |
| 4 | Feedback urgente | `POST /api/avaliacao` com `{"descricao":"Sistema fora do ar","nota":2,"urgencia":"ALTA"}` | `201`; item no Cosmos DB; e-mail de urgência recebido |
| 5 | Listagem | `curl BASE/api/avaliacao` | `200` e array com os feedbacks |
| 6 | Relatório | Portal → `relatorio-semanal` → Código + Teste → Executar | `202`; e-mail do relatório com média e contagens |
| 7 | Alerta | Seção 10, "Como testar o alerta" | Alerta disparado em Monitor → Alertas e e-mail ao administrador |
| 8 | Deploy | Push na `main` | Workflow verde na aba Actions |

**Nota sobre o teste 6:** o relatório cobre a semana anterior. Para a execução manual não sair vazia, é necessário haver feedbacks com `dataEnvio` na semana anterior (podem ser inseridos pelo Data Explorer do Cosmos DB no formato `yyyy-MM-ddTHH:mm:ss`).
 
---

## 13. Execução local

### Pré-requisitos

- JDK 21 e Maven
- Azure Functions Core Tools v4
- Azurite (para fila e timer)
### API Spring Boot

```bash
mvn spring-boot:run
```

Disponível em `http://localhost:8080`, com os endpoints em `/avaliacao` e `/avaliacao/health`. Inclui `springdoc-openapi`: especificação em `/v3/api-docs` e Swagger UI em `/swagger-ui.html`.

### Azure Functions

Com o Azurite em execução e `AzureWebJobsStorage` configurado em `local.settings.json`:

```bash
mvn azure-functions:run
```

Disponível em `http://localhost:7071/api/...`.

> As Functions inicializam o contexto Spring completo (`SpringApplication.run`) e, como `spring-boot-starter-web` está no classpath, ele sobe um Tomcat na porta 8080. Não execute `mvn spring-boot:run` e `mvn azure-functions:run` ao mesmo tempo na mesma máquina.

### Testes e build

```bash
mvn clean verify
```
 
---

## 14. Limitações conhecidas

| Área | Limitação | Melhoria sugerida |
| --- | --- | --- |
| Inicialização | O contexto Spring Web (Tomcat na porta 8080) é iniciado dentro do worker das Functions, aumentando o cold start | Definir `spring.main.web-application-type=none` para o perfil das Functions |
| Tratamento de erros | Nas Functions, JSON malformado ou `urgencia` inválida resulta em `500`; na API Spring resulta em `400`. Os corpos de erro também diferem | Capturar `JsonProcessingException` e retornar `400`; padronizar com `@RestControllerAdvice` |
| Exposição de erros | Respostas `500` incluem `ex.getMessage()` | Retornar mensagem genérica e manter o detalhe apenas no log |
| Autorização | Todas as rotas HTTP são `ANONYMOUS`, inclusive a listagem | `AuthorizationLevel.FUNCTION` ou autenticação (Easy Auth, API Management) nas rotas administrativas |
| Segredos | A chave do Cosmos DB e a connection string do ACS ficam em variáveis de ambiente (legíveis por quem tem acesso à Function App) | Managed Identity no Cosmos DB e referências ao Azure Key Vault |
| Governança | Apenas a conta responsável pela assinatura tem acesso ao portal | Conceder a função Colaborador aos integrantes no Resource Group |
| Consistência | Gravação no Cosmos DB e publicação na fila não são atômicas; se a fila falhar após a gravação, o feedback fica salvo sem alerta | Padrão outbox ou Change Feed do Cosmos DB como origem do evento |
| Consulta | `findAll()` executa `SELECT * FROM c` sem paginação (consulta entre partições com a chave `/id`); o relatório filtra a semana em memória | Filtrar por `dataEnvio` na query e paginar a listagem |
| E-mail | O resultado final de `waitForCompletion()` não é inspecionado; destinatário único | Validar o status final do envio; suportar lista de destinatários |
| Health | `/health` apenas retorna `UP`, sem verificar Cosmos DB ou fila | Verificação de dependências (readiness) |
| Fuso do relatório | O timer usa UTC (09:00 UTC = 06:00 em Brasília) | Ajustar a expressão ou definir `WEBSITE_TIME_ZONE` |
| Alerta | Há uma única regra de alerta (falhas de execução) | Regras adicionais para `exceptions`, tempo de resposta e mensagens na fila de poison |
| `pom.xml` | O bloco do plugin Maven descreve um ambiente diferente do publicado | Alinhar `appName`, `resourceGroup`, `region` e `os` ao ambiente real |
 
---

## 15. Estrutura do código

```text
src/main/java/com/fiap/feedback/
|-- config/       # Configuração do cliente Cosmos DB
|-- controller/   # Endpoints REST (Spring, execução local)
|-- dto/          # FeedbackRequest e WeeklyFeedbackReport
|-- enums/        # Urgencia (normalização de caixa e acentos)
|-- function/     # Triggers do Azure Functions (HTTP, Queue, Timer)
|-- model/        # Modelo de feedback
|-- repository/   # Persistência no Cosmos DB
`-- service/      # Regras de negócio, relatório e e-mail
 
.github/workflows/deploy.yml   # Pipeline de deploy automatizado
```

A camada de `service` concentra as regras de negócio e é reutilizada pela API Spring e pelas Functions. As Functions inicializam o contexto Spring em um inicializador estático e obtêm os beans (serviços, `ObjectMapper`, `Validator`) a partir dele, mantendo os triggers restritos a parse, validação e montagem da resposta.
 
---

## 16. Autores

| Nome | RM |
| --- | --- |
| Thiago Vanzele | RM374158 |
| Luiz Eduardo Gambeti | RM373030 |
| Sandoval Bento da Silva | RM370706 |
 