# Execuções assíncronas de cálculo por competência

## Endpoints

### Solicitar execução

`POST /api/v1/execucoes/calculo`

Use uma chave de idempotência no cabeçalho `Idempotency-Key` ou no corpo. A resposta é `202 Accepted` após validar a competência, a cobertura de taxa base e gerar as referências consistentes de dados e regras.

```json
{
  "competencia": "2026-10",
  "recalcular": false,
  "chaveIdempotencia": "calculo-2026-10-v1"
}
```

### Consultar status e progresso

`GET /api/v1/execucoes/calculo/{id}`

Estados possíveis: `SOLICITADA`, `PROCESSANDO`, `CONCLUIDA` e `FALHOU`.

O retorno contém totais, percentual de progresso, hashes das referências congeladas, falha e resumo do resultado. O progresso é confirmado em transações independentes do cálculo: durante `PROCESSANDO`, indica itens analisados, sem confirmar resultados financeiros. Fica limitado a 99% até o commit da conclusão; `CONCLUIDA` indica 100%, inclusive sem vendas.

### Retomar após falha

`POST /api/v1/execucoes/calculo/{id}/retomar`

Somente execuções em `FALHOU` podem ser retomadas. A retomada revalida fechamento e cobertura, cria novas referências de dados/regras e uma nova identificação interna de tentativa antes de enfileirar o processamento. Callbacks antigos não podem alterar essa tentativa. As referências retornadas indicam qual conjunto foi solicitado.

## Garantias operacionais

- A mesma chave de idempotência retorna a execução original. Reutilizar a chave para outra competência ou outro modo de recálculo retorna `409 Conflict`.
- Só há uma execução ativa (`SOLICITADA` ou `PROCESSANDO`) por competência. Solicitações incompatíveis retornam `409 Conflict`.
- Na solicitação, retomada e início do processamento, conferir bloqueios de fechamento; competência fechada ou em fechamento não aceita novo cálculo. Consultas históricas continuam nos endpoints existentes.
- Antes de calcular, validar cobertura e conferir referências canônicas de vendas, RH (matrícula, cargo, loja e datas), bases contratuais dos públicos envolvidos, resultados reaproveitáveis e campos das regras ativas (filtros, condições, operadores, taxas, operações, bônus e versão vigente). Alterações em uma proposta não publicada não mudam a referência da vigente.
- As referências distinguem valores nulos e preservam zeros de matrícula; diferenças apenas de escala decimal não geram mudanças artificiais. Bases de outro público não invalidam a solicitação.
- A chave de idempotência é conferida novamente após a espera pela trava. Colisões entre competências retornam 409 após rollback, sem deixar transação abortada. O índice parcial garante uma única execução ativa por competência no PostgreSQL.
- O pedido é confirmado antes de enfileirar. Rejeição da fila fica registrada como `FALHOU`, sem resultado e sem sucesso fictício.
- Se dados ou regras mudarem entre a solicitação e o início do job, a execução falha sem calcular resultados parciais.
- A trava pessimista por competência é adquirida antes de ler vendas, regras e cobertura; exclusões e alterações que usam a mesma trava aguardam a conclusão.
- O executor local é configurado com 2 threads-base, máximo de 4 e fila de 50 tarefas em `ExecucaoCalculoConfig`.

## Ambiente

- O schema é criado pela migração `V26__criar_execucoes_calculo_assincronas.sql`, posterior ao histórico V24 e fechamento V25; migrações já aplicadas são preservadas.
- A aplicação requer Java 21.
- Para validar a compilação e os testes:

```powershell
cd C:\Users\Autaza\Fatec\apis\api-6\API-6-backend\backend
.\mvnw.cmd test
```

## Operação e diagnóstico

- Consulte a execução pelo ID retornado no `202` até ela atingir `CONCLUIDA` ou `FALHOU`.
- Em falhas de cobertura, importe/cadastre as taxas base faltantes e use o endpoint de retomada.
- Em falhas por alteração de referência, retome a execução para criar referências atuais, ou envie um novo pedido após corrigir a causa. Este recurso calcula a competência; não substitui a operação de fechamento final do #27.
- Em ambiente com mais de uma instância, todas devem compartilhar PostgreSQL. Travas, unicidade e tentativa impedem execução duplicada e interferência de callbacks antigos; a fila continua local ao processo que recebeu a solicitação.
- A aplicação aguarda tarefas por até 30 segundos no encerramento gerenciado. Esta entrega não inclui fila externa, lease/heartbeat ou recuperação automática de pedidos interrompidos por queda abrupta do processo. Execuções deixadas em `SOLICITADA`/`PROCESSANDO` exigem recuperação operacional antes da retomada; não criar outra execução em paralelo para contornar a proteção.
- O progresso é atualizado aproximadamente a cada 1% dos itens (ou a cada item em conjuntos pequenos). Uma falha pode manter a contagem analisada, mas não confirma os resultados: eles são revertidos junto da transação financeira.
