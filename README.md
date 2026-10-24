# Feedback Platform

API de feedbacks desenvolvida com Java 21, Spring Boot e Azure Functions. Os dados são persistidos no Azure Cosmos DB e as operações principais também estão expostas como funções serverless.

## Tecnologias

- Java 21
- Spring Boot 3.3.5
- Maven
- Azure Cosmos DB
- Azure Functions Runtime 4
- Azure Communication Services Email

## Configuração do Azure Cosmos DB

A aplicação precisa de um banco de dados e de um container já criados no Cosmos DB. Configure as variáveis de ambiente abaixo:

| Variável | Obrigatória | Descrição | Padrão |
| --- | --- | --- | --- |
| `AZURE_COSMOS_ENDPOINT` | Sim | Endpoint da conta Cosmos DB | Endpoint configurado em `application.yml` |
| `AZURE_COSMOS_DATABASE` | Não | Nome do banco de dados | `feedback-db` |
| `AZURE_COSMOS_CONTAINER` | Não | Nome do container | `feedback-container` |
| `AZURE_COSMOS_KEY` | Não | Chave da conta Cosmos DB | Sem valor |
| `AZURE_COMMUNICATION_CONNECTION_STRING` | Sim, para e-mails | Connection string do Azure Communication Services Email | Sem valor |
| `AZURE_EMAIL_SENDER` | Sim, para e-mails | Endereço remetente do domínio provisionado no Azure Communication Services | Sem valor |
| `AZURE_NOTIFICATION_EMAIL` | Necessária para alertas | E-mail do administrador destinatário | Sem valor |

Configure `AZURE_COMMUNICATION_CONNECTION_STRING` como segredo nas configurações da Function App e `AZURE_EMAIL_SENDER` com o remetente provisionado para o recurso de Email. Não grave a connection string no código-fonte nem a publique em repositório.

Quando `AZURE_COSMOS_KEY` não for definida, a aplicação usa `DefaultAzureCredential`. Nesse caso, configure uma identidade Azure com as permissões necessárias para acessar o Cosmos DB. Não armazene chaves ou credenciais no código-fonte.

Exemplo para execução local em Linux/macOS:

```bash
export AZURE_COSMOS_ENDPOINT="https://<sua-conta>.documents.azure.com:443/"
export AZURE_COSMOS_DATABASE="feedback-db"
export AZURE_COSMOS_CONTAINER="feedback-container"
# Opcional: use a chave localmente ou autenticação por identidade Azure.
export AZURE_COSMOS_KEY="<sua-chave>"
export AZURE_COMMUNICATION_CONNECTION_STRING="<connection-string-do-communication-services>"
export AZURE_EMAIL_SENDER="<remetente-do-dominio-provisionado>"
export AZURE_NOTIFICATION_EMAIL="<email-destinatario>"
```

## Executar localmente

### API Spring Boot

```bash
mvn spring-boot:run
```

A API Spring fica disponível em `http://localhost:8080` e expõe os endpoints REST sob `/avaliacao`.

### Azure Functions

Antes de iniciar a Function localmente, garanta que o `AzureWebJobsStorage` esteja configurado em `local.settings.json` e que o Azurite esteja em execução.

```bash
mvn azure-functions:run
```

As funções locais ficam em `http://localhost:7071/api/...`.

### Testes e build

```bash
mvn clean verify
```

## API Spring

### `POST /avaliacao`

Cria um feedback. `descricao`, `nota` e `urgencia` são obrigatórios. A nota deve estar entre 0 e 10. A urgência aceita `BAIXA`, `MEDIA` ou `ALTA`, sem distinção entre maiúsculas e minúsculas; também aceita a forma acentuada `Média`.

```bash
curl -X POST http://localhost:8080/avaliacao \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Atendimento muito bom",
    "nota": 9,
    "urgencia": "Alta"
  }'
```

### `GET /avaliacao`

Lista os feedbacks salvos.

### `GET /avaliacao/health`

Retorna o status de saúde da API Spring.

## Azure Functions

O projeto implementa as funções abaixo, conforme a classe `FeedbackFunction`.

| FunctionName | Gatilho | Endpoint / comportamento |
| --- | --- | --- |
| `avaliacao-post` | `HttpTrigger` `POST` | Cria um feedback em `/api/avaliacao` |
| `avaliacao-list` | `HttpTrigger` `GET` | Lista os feedbacks em `/api/avaliacao` |
| `health` | `HttpTrigger` `GET` | Verifica saúde em `/api/health` |
| `notificacao-urgencia` | `QueueTrigger` em `urgency-notifications` | Consome mensagens da fila e envia e-mail para urgência `ALTA` |
| `relatorio-semanal` | `TimerTrigger` `0 0 9 * * MON` | Gera e envia o relatório semanal na segunda-feira às 09:00 UTC |

Ao receber um feedback com urgência `ALTA`, a function `avaliacao-post` publica os dados em `urgency-notifications`, uma fila do Azure Storage. A function `notificacao-urgencia` consome a mensagem e envia um e-mail ao administrador configurado com descrição, urgência e data de envio. Se o envio falhar, a exceção é registrada para que o runtime do Azure Functions possa repetir o processamento da mensagem.

### Rotas HTTP públicas da Azure Function

| Método | Rota | Função |
| --- | --- | --- |
| `POST` | `/api/avaliacao` | `avaliacao-post` |
| `GET` | `/api/avaliacao` | `avaliacao-list` |
| `GET` | `/api/health` | `health` |

### Relatório semanal

A function `relatorio-semanal` é executada toda segunda-feira às 09:00 UTC. Ela considera a semana completa anterior (segunda a domingo) e envia um relatório com os feedbacks, a média das notas, a quantidade por dia e a quantidade por urgência ao endereço `AZURE_NOTIFICATION_EMAIL`. Os logs registram somente o período, o total de avaliações e a média, sem incluir descrições dos feedbacks.

O Timer Trigger e a fila usam a conexão `AzureWebJobsStorage` do Azure Functions, declarada explicitamente nos bindings. Em execução local, essa variável já está definida em `local.settings.json` como `UseDevelopmentStorage=true`; portanto, o Azurite precisa estar instalado e em execução. Na Function App publicada, configure `AzureWebJobsStorage` com a connection string de uma conta Azure Storage válida.

Para executar localmente como Azure Functions, é necessário ter o Azure Functions Core Tools instalado:

```bash
mvn azure-functions:run
```

Para empacotar e publicar:

```bash
mvn clean package
mvn azure-functions:deploy
```

O plugin Maven em `pom.xml` está configurado para Azure Functions em Linux, Java 21 e Functions Runtime 4. O nome do aplicativo, grupo de recursos e região também estão definidos nesse arquivo. A publicação requer autenticação Azure e configuração das variáveis do Cosmos DB nas configurações da Function App.

> **Atenção:** a configuração Maven prepara a publicação como Azure Functions, mas `.azure/default/deployment.targets.xml` aponta para um alvo do tipo Azure App Service. Esses alvos são diferentes; confirme ou atualize o alvo salvo no Azure Toolkit antes de publicar por ele. Os comandos Maven acima usam o plugin de Azure Functions.

## Estrutura principal

```text
src/main/java/com/fiap/feedback/
├── config/       # Configuração do cliente Cosmos DB
├── controller/   # Endpoints REST Spring
├── dto/          # Contrato e validação da requisição
├── function/     # Triggers HTTP do Azure Functions
├── model/        # Modelo de feedback
├── repository/   # Persistência no Cosmos DB
└── service/      # Regras de negócio
```

## Autores

| Nome | RM |
| --- | --- |
| Thiago Vanzele | RM374158 |
| Luiz Eduardo Gambeti | RM373030 |
| Sandoval Bento da Silva | RM370706 |
