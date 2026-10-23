# Importação assíncrona com Spring Batch e consulta de relatório

## Título sugerido para o merge request

`feat: importar bases com Spring Batch e consultar status pelo reportId`

## Descrição

O upload de bases de RH, vendas e comissões aguardava todo o processamento antes de responder. Os readers montavam listas com todas as linhas e vendas sem matrícula cadastrada eram descartadas silenciosamente. A criação de cadastros auxiliares pelos mappers também não estava protegida por uma transação abrangendo o arquivo inteiro.

O fluxo passa a iniciar uma importação assíncrona com Spring Batch. O upload retorna `202 Accepted`, um `reportId` e a URL de consulta no header `Location`. O front pode consultar o relatório por polling até o processamento terminar. A gravação é transacional: uma falha desfaz os registros da importação, incluindo lojas, cargos e marcas criados durante o processamento.

## Alterações realizadas

- Substituição do starter Batch pela variante `spring-boot-starter-batch-jdbc`, com metadados persistidos nas tabelas `BATCH_*`.
- Configuração de `importJob`, `importStep` e execução assíncrona pelo `JobOperator`.
- Executor com um worker e fila de até 20 importações aguardando execução. As importações são serializadas dentro desta instância da aplicação para reduzir concorrência na criação dos cadastros auxiliares.
- Processamento das três bases por um Tasklet, em uma única transação por arquivo.
- Leitura Excel compartilhada entre os readers, sem listas intermediárias de DTOs no fluxo de importação.
- `flush` e `clear` do contexto JPA a cada 100 registros, sem commits intermediários.
- Arquivo temporário mantido até o término do job e removido após sucesso ou falha. Também há limpeza se a submissão falhar ou a fila estiver cheia.
- Novo endpoint de consulta e documentação dos contratos no Swagger.
- Persistência de mensagens de validação no contexto da execução para consulta posterior.
- Preenchimento de `id_lote_origem` nas vendas importadas com o identificador interno da submissão.

## Contrato da API

### Iniciar a importação

```http
POST /api/v1/imports/upload
Content-Type: multipart/form-data
```

Parâmetros obrigatórios:

| Parâmetro | Conteúdo |
| --- | --- |
| `file` | Planilha Excel |
| `importType` | `HR`, `SALES` ou `COMISSIONS` |

Exemplo de resposta:

```http
HTTP/1.1 202 Accepted
Location: /api/v1/imports/123
```

```json
{
  "reportId": 123,
  "nomeArquivo": "BASE_COMMISS_FINAL.xlsx",
  "tipoBase": "COMISSIONS",
  "status": "PENDENTE",
  "totalLinhas": null,
  "mensagem": null
}
```

O estado retornado é uma consulta aos metadados da execução. Um arquivo pequeno pode estar em processamento ou já ter terminado quando o upload responder. `202` confirma o recebimento da importação; o resultado deve ser conferido no relatório.

### Consultar o relatório

```http
GET /api/v1/imports/123
```

O `reportId` corresponde ao ID da execução do Spring Batch. A consulta retorna `200 OK` com `Cache-Control: no-store`. Um ID inexistente retorna `404 Not Found`.

| Status | Significado | `totalLinhas` |
| --- | --- | --- |
| `PENDENTE` | Importação aguardando execução | `null` |
| `PROCESSANDO` | Processamento em andamento | `null` |
| `CONCLUIDO` | Importação concluída | Quantidade de registros confirmados |
| `FALHOU` | Importação não concluída | `0` |

Exemplo de sucesso para uma planilha com 30 registros válidos:

```json
{
  "reportId": 123,
  "nomeArquivo": "BASE_COMMISS_FINAL.xlsx",
  "tipoBase": "COMISSIONS",
  "status": "CONCLUIDO",
  "totalLinhas": 30,
  "mensagem": null
}
```

O front pode consultar a cada 2–3 segundos e interromper o polling em `CONCLUIDO` ou `FALHOU`. Esse intervalo é uma orientação para o cliente; o backend não agenda as consultas.

## Validações implementadas

| Base ou situação | Validação |
| --- | --- |
| Upload | Arquivo não vazio e tipo de importação informado |
| Excel | Arquivo legível, existência de aba e de linhas de dados |
| RH | Matrícula, código da loja, código do cargo e data de admissão obrigatórios |
| Vendas | Matrícula, código da loja, código da marca, data de referência e valor da venda obrigatórios |
| Vendas | Matrícula deve estar cadastrada; a linha não é descartada silenciosamente |
| Comissões | Código da marca, código do cargo e percentual obrigatórios |
| Banco | Restrições de integridade, incluindo unicidade de matrícula no RH |

Um arquivo de tamanho zero é rejeitado no upload com `400`. Falhas encontradas durante a leitura e validação da planilha aparecem no relatório com `FALHOU`. Erros de campos obrigatórios e matrícula desconhecida incluem o número da linha. Violações de integridade do banco retornam uma mensagem genérica no relatório, sem expor SQL ou stack trace.

## Como executar para teste manual

Pré-requisitos: Java 21, Maven e Docker com Compose para disponibilizar o PostgreSQL.

Na raiz do repositório, execute em um terminal Bash:

```bash
cd backend
docker compose up -d postgres
DB_HOST=localhost DB_NAME=bugbusters_db mvn spring-boot:run
```

Esses valores correspondem ao banco definido em `backend/docker-compose.yml`. O perfil `dev` utiliza Flyway para o schema, incluindo as tabelas Batch da migração existente `V3__create_batch_schema.sql`. Aguarde o banco ficar disponível antes de iniciar a aplicação.

Caso a aplicação já esteja rodando com a versão anterior, reinicie-a para carregar os endpoints atualizados. Abra:

```text
http://localhost:8080/swagger-ui/index.html
```

### Teste de sucesso pelo Swagger

1. Abra **Ingestão de Dados → POST /api/v1/imports/upload** e clique em **Try it out**.
2. Selecione `BASE_COMMISS_FINAL.xlsx`, disponível na raiz do repositório.
3. Informe `COMISSIONS` em `importType` e clique em **Execute**.
4. Confira o HTTP `202`, o `reportId` no JSON e o header `Location`.
5. Abra **GET /api/v1/imports/{reportId}**, informe o ID retornado e clique em **Execute**.
6. Repita a consulta até `CONCLUIDO` ou `FALHOU`. O Swagger executa essas consultas manualmente.
7. Em caso de sucesso, confira `totalLinhas` com a quantidade de linhas de dados da primeira aba, desconsiderando o cabeçalho e as linhas vazias.

As colunas são posicionais. A primeira linha é tratada como cabeçalho. Para testar outras bases, mantenha esta ordem:

| Base | Ordem das colunas |
| --- | --- |
| `HR` | Data de referência, código da marca, descrição da marca, código da loja, descrição da loja, matrícula, admissão, demissão, código do cargo, descrição do cargo |
| `SALES` | Data de referência, código da marca, descrição da marca, código da loja, descrição da loja, matrícula, valor da venda |
| `COMISSIONS` | Código da marca, descrição da marca, código do cargo, descrição do cargo, percentual |

Para vendas, cadastre ou importe previamente a matrícula usada na planilha. Use descrições preenchidas, códigos numéricos inteiros e valores/percentuais em células numéricas para os testes de sucesso.

### Cenários de falha

| Cenário | Como testar | Resultado esperado |
| --- | --- | --- |
| Excel sem dados | Enviar uma planilha apenas com cabeçalho | Upload `202`; relatório `FALHOU`, `totalLinhas: 0` e mensagem de arquivo sem dados |
| Campo obrigatório ausente | Limpar o percentual de uma linha de comissões | Relatório `FALHOU`, com número da linha e campo ausente |
| Matrícula desconhecida | Enviar vendas com matrícula não cadastrada | Relatório `FALHOU`, com número da linha e matrícula não cadastrada |
| Matrícula duplicada | Enviar RH com duas linhas usando a mesma matrícula | Relatório `FALHOU`; nenhuma das linhas da importação é confirmada |
| Arquivo inválido | Enviar um arquivo de texto como planilha | Relatório `FALHOU`, com mensagem de Excel inválido ou não suportado |
| Report inexistente | Consultar um ID que não exista | HTTP `404` |

Para verificar o rollback, em um ambiente de teste isolado registre as contagens abaixo antes do upload. Envie uma planilha de comissões com uma primeira linha válida usando códigos novos de marca/cargo e uma segunda linha sem percentual. Aguarde `FALHOU` e repita a consulta: as contagens devem permanecer iguais.

```sql
SELECT COUNT(*) FROM tb_basecomiss;
SELECT COUNT(*) FROM tb_brands;
SELECT COUNT(*) FROM tb_position;
```

## Testes automatizados

Na raiz do repositório:

```bash
cd backend
mvn test
```

Para executar apenas os testes de importação e de rotas:

```bash
mvn test -Dtest=ImportBatchIntegrationTest,ControllerRoutesTest
```

A última execução da suíte completa passou com **102 testes, sem falhas ou erros**. Os testes de integração usam H2 e verificam:

- Importação de RH, vendas e comissões, com metadados Batch persistidos.
- Retorno do `reportId`, HTTP `202`, header `Location` e consulta dos resultados.
- Relatório pendente enquanto o worker está ocupado e retenção do arquivo até o processamento.
- Mensagens de validação recuperadas pela consulta ao banco.
- Rollback após mais de 100 linhas, incluindo cadastros auxiliares.
- Limpeza de arquivos temporários após processamento e quando a fila está cheia.
- HTTP `404` para relatório inexistente.
- Presença da rota GET e da resposta `202` na documentação OpenAPI.

Os resultados automatizados não substituem o teste manual com PostgreSQL. Não foi executada uma suíte de integração específica com PostgreSQL nesta alteração.

## Impactos e limites

- **Mudança de contrato:** o front deve substituir a expectativa de `201` com resultado final por `202` com `reportId`, seguido da consulta do relatório.
- `totalLinhas` representa registros confirmados, não progresso por linha. Fica `null` enquanto a execução não termina.
- A primeira aba é processada por posição das colunas; CSV e validação de nomes dos cabeçalhos não estão implementados.
- O POI ainda carrega o workbook em memória. A remoção das listas intermediárias e o `clear` do JPA reduzem estruturas adicionais, mas não tornam a leitura totalmente streaming.
- O Tasklet mantém uma transação por arquivo para garantir rejeição integral. Chunks com commits intermediários exigiriam staging para preservar esse contrato.
- Os metadados dos relatórios ficam no banco. Os arquivos são temporários, e não há retomada automática após interrupção da aplicação; um processamento interrompido exige tratamento operacional e novo upload.
- A serialização das importações vale por instância; não há coordenação de múltiplas instâncias nesta alteração.
- A fila cheia gera um relatório `FALHOU` e remove o arquivo enviado.
- Não há deduplicação por conteúdo do arquivo nem critérios de duplicidade de negócio para vendas e comissões. Limites de percentual, valores negativos e coerência entre admissão/demissão também ficam fora desta alteração.
