# Guia rápido — correções do PR #26

Branch: `feat/async--calculation`, incorporando a dev `9109cfa` com os PRs #25 e #27. Alterações somente no backend, testes e documentação. Sem mudanças no frontend ou na IA, sem reiniciar os serviços em uso.

## Entrega

- Migração V26, sem conflito com V23/V24/V25. Inclui identificação da tentativa e índice único parcial de execução ativa por competência.
- Conflito no motor resolvido mantendo os bloqueios e a preservação de consultas históricas aprovados no #27.
- Referências canônicas incluem vendas, RH, bases contratuais, resultados reutilizáveis e campos das regras ativas: taxas, filtros, faixas, inclusividade, operação, bônus e versão vigente. O detalhe auditável vem dos dados existentes; não são chamadas novas interpretações da IA.
- Matrículas permanecem texto, valores nulos têm representação própria e decimais são normalizados sem diferenças artificiais de escala. A serialização não usa concatenação ambígua de campos.
- A consulta de vendas busca seus vínculos em conjunto. Bases de outros públicos e propostas não publicadas não invalidam a referência da vigente.
- Idempotência protegida após adquirir a trava, incluindo colisões simultâneas entre competências diferentes. Retomadas usam trava da execução e nova tentativa; tarefas/callbacks antigos são ignorados.
- Fechamento é conferido ao solicitar, retomar e executar. A mesma trava protege a conferência de referências e o cálculo. Regras e dados alterados enquanto o pedido espera impedem executar com um conjunto misto.
- Estado `PROCESSANDO` e progresso ficam visíveis por consultas independentes. O cálculo e sua conclusão são atômicos; falhas revertem seus resultados e logs parciais. `FALHOU` é registrado depois do rollback.
- O progresso representa itens analisados. Até o commit final, fica abaixo de 100%; apenas `CONCLUIDA` confirma o resultado. Rejeição da fila também retorna estado de falha.

## Integração futura da interface

Usar os endpoints existentes do PR:

1. `POST /api/v1/execucoes/calculo` com `{ "competencia": "2026-10", "recalcular": false, "chaveIdempotencia": "calculo-2026-10-v1" }`.
2. Consultar `GET /api/v1/execucoes/calculo/{id}` até estado terminal.
3. Em falha corrigível, `POST /api/v1/execucoes/calculo/{id}/retomar` cria novas referências para os dados atuais.

Manter a mesma chave em tentativas de envio de um mesmo pedido; outra competência ou modo de recálculo com a mesma chave retorna 409. Não transformar qualquer resposta 202 em sucesso do cálculo: verificar `status` e acompanhar a execução. O cabeçalho `Idempotency-Key`, quando informado, tem prioridade sobre o corpo.

Apresentar progresso como acompanhamento, sem totais financeiros finais antes de `CONCLUIDA`. Em `FALHOU`, preservar o contexto do pedido, mostrar o motivo e permitir retomada quando a competência estiver disponível. Uma competência em fechamento/fechada deve informar o bloqueio e manter consultas históricas acessíveis.

Este fluxo é cálculo por competência. Não substitui simulação de campanha nem o fechamento final do #27.

## Como verificar

- Enviar o mesmo pedido duas vezes e em paralelo: um único ID e uma única execução enfileirada.
- Reutilizar chave com competência ou modo diferente: 409.
- Alterar canal, valor, RH, base, filtros, faixas, operação, bônus ou versão vigente enquanto o pedido está na fila: falha sem gravar novos resultados.
- Alterar apenas uma proposta não publicada ou base de outro público: a vigente continua utilizável.
- Acompanhar progresso em outra consulta durante o processamento: itens avançam e resultados financeiros permanecem sem confirmação.
- Provocar falha após o cálculo: resultados e logs parciais são revertidos; a execução fica `FALHOU`. Retomar com a causa corrigida utiliza novas referências.
- Disparar callback antigo após uma retomada: não pode sobrescrever a tentativa nova.
- Fechar ou iniciar fechamento da competência antes de executar: cálculo e retomada ficam bloqueados.

## Verificação automatizada

Suíte geral em Java 21: 390 testes, 383 aprovados e 7 ignorados, sem falhas ou erros. Os ignorados são o reparo Flyway sem banco, quatro testes opcionais de migração e os dois testes dos índices parciais PostgreSQL.

Execução adicional em PostgreSQL 16 descartável: 41 testes aprovados, sem ignorados — 31 do cálculo assíncrono, seis de transações de fechamento e quatro de migração. O pacote da aplicação foi gerado com sucesso (`mvn -DskipTests package`). A aplicação de testes inicia com Flyway e valida o esquema Hibernate. Foram conferidas instalação até V26 e atualização V25 → V26, sem usar o banco da aplicação nem chamar a IA.

`ExecucaoCalculoIntegrationTest` usa H2 isolado por padrão. Para PostgreSQL descartável, definir `EXECUCAO_TEST_DB_URL`, `EXECUCAO_TEST_DB_USER`, `EXECUCAO_TEST_DB_PASSWORD` e executar `mvn -Dtest=ExecucaoCalculoIntegrationTest test`. O teste específico do índice parcial só roda nesse modo.

Os testes de migração com `MIGRATION_TEST_DB_URL` incluem atualização V25 → V26 preservando campanha e fechamento existentes. As verificações de fechamento podem ser repetidas com `FECHAMENTO_TEST_DB_URL`. Não apontar essas variáveis para o banco de uso da aplicação.

## Limite operacional

O executor continua local, com duas threads-base, máximo de quatro e fila de 50 tarefas. Há espera de encerramento gerenciado de 30 segundos. Não foi adicionada fila externa ou recuperação automática após queda abrupta do processo: pedidos que permaneçam `SOLICITADA`/`PROCESSANDO` precisam de recuperação operacional antes de usar a retomada. Compartilhar o banco coordena as travas entre instâncias, mas não torna a fila durável.

As interfaces das duas tarefas de edição/revisão de campanhas e os contratos adicionais de proposta continuam na etapa seguinte; este PR corrige a infraestrutura de cálculo assíncrono.
