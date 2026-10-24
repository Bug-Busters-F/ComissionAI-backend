# Execuções assíncronas de cálculo por competência

## Endpoints

### Solicitar execução

`POST /api/v1/execucoes/calculo`

Use uma chave de idempotência no cabeçalho `Idempotency-Key` ou no corpo. A resposta é `202 Accepted` após validar a competência, a cobertura de taxa base e gerar as referências consistentes de dados e regras.

```json
{
  "competencia": "2026-10",
  "recalcular": false,
  "chaveIdempotencia": "fechamento-2026-10-v1"
}
```

### Consultar status e progresso

`GET /api/v1/execucoes/calculo/{id}`

Estados possíveis: `SOLICITADA`, `PROCESSANDO`, `CONCLUIDA` e `FALHOU`.

O retorno contém totais, percentual de progresso, hashes das referências congeladas, falha e resumo do resultado.

### Retomar após falha

`POST /api/v1/execucoes/calculo/{id}/retomar`

Somente execuções em `FALHOU` podem ser retomadas. A retomada revalida cobertura e cria novas referências de dados e regras antes de enfileirar o processamento novamente.

## Garantias operacionais

- A mesma chave de idempotência retorna a execução original. Reutilizar a chave para outra competência ou outro modo de recálculo retorna `409 Conflict`.
- Só há uma execução ativa (`SOLICITADA` ou `PROCESSANDO`) por competência. Solicitações incompatíveis retornam `409 Conflict`.
- Antes do aceite da solicitação e no início do processamento, o sistema valida cobertura de comissão base e calcula hashes de vendas e regras ativas.
- Se dados ou regras mudarem entre a solicitação e o início do job, a execução falha sem calcular resultados parciais.
- A trava pessimista por competência é adquirida antes de ler vendas, regras e cobertura; exclusões e alterações que usam a mesma trava aguardam a conclusão.
- O executor local é configurado com 2 threads-base, máximo de 4 e fila de 50 tarefas em `ExecucaoCalculoConfig`.

## Ambiente

- O schema é criado pela migração `V23__criar_execucoes_calculo_assincronas.sql`.
- A aplicação requer Java 21.
- Para validar a compilação e os testes:

```powershell
cd C:\Users\Autaza\Fatec\apis\api-6\API-6-backend\backend
.\mvnw.cmd test
```

## Operação e diagnóstico

- Consulte a execução pelo ID retornado no `202` até ela atingir `CONCLUIDA` ou `FALHOU`.
- Em falhas de cobertura, importe/cadastre as taxas base faltantes e use o endpoint de retomada.
- Em falhas por alteração de referência, envie uma nova solicitação; isso garante que o fechamento use um snapshot coerente.
- Em ambiente com mais de uma instância, a tabela de trava por competência e a tabela de execuções devem apontar para o mesmo banco de dados PostgreSQL.
