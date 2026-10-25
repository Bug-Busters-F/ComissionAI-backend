# Guia rápido — correções do PR #27

Branch: `feat/fechamento-apuracao-final`. A base corrigida do PR #25 foi incorporada localmente, incluindo a dev com rastreabilidade/progresso. Integrar primeiro o #25 e depois o #27. Alterações somente no backend e nos seus testes/documentação; sem mudança no frontend ou na IA.

## Comportamento corrigido

- A nova migração é V25, preservando V22, V23 e V24. Cria a estrutura de fechamento e os vínculos nos resultados. Um índice único parcial impede dois fechamentos em processamento/concluídos para a mesma competência, preservando múltiplas tentativas com falha.
- A reserva `PROCESSANDO` é gravada e confirmada antes da apuração, ficando visível pela consulta de status durante a execução.
- Apuração e conclusão compartilham uma transação: erro reverte suas escritas parciais. O registro `FALHOU`, com protocolo e motivo, é gravado em outra transação após o rollback, sem desaparecer junto com o cálculo.
- A competência só fica liberada para nova tentativa depois de a falha ser registrada. Um fechamento concluído continua bloqueado.
- A mesma trava é usada no início do fechamento, no cálculo e nas alterações de campanha. As verificações são feitas depois de adquirir a trava e ela é mantida até o commit da alteração.
- Operações que envolvem vários meses adquirem a união das competências em ordem crescente. Edição, alteração de estado, exclusão e publicação verificam tanto a vigência corrente quanto a proposta. Mudar o período da proposta não contorna um mês fechado da vigente.
- A inicialização concorrente de uma trava usa inserção atômica, sem capturar uma violação de unicidade que deixaria a transação PostgreSQL abortada.
- Cálculo avulso confere o mês solicitado e o mês original da venda persistida: informar outra data não contorna fechamento. Cálculo avulso, por venda e em lote impedem novas apurações em competências fechadas/em processamento. O recálculo forçado continua bloqueado. Consultas de resultados existentes continuam disponíveis; consultar um mês fechado não recalcula vendas novas ou taxa contratual atual.
- Os testes de rotas usam bancos H2 exclusivos e limpeza de fixtures por JDBC; a proibição produtiva de excluir logs imutáveis permanece ativa.

## Contratos para a interface

Não há mudança nos endpoints ou no formato dos pedidos existentes:

- `POST /api/v1/comissoes/fechamento`: executa o fechamento e devolve totais/protocolo.
- `GET /api/v1/comissoes/fechamento/{competencia}/status`: informa estado, `bloqueada` e motivo.
- `GET /api/v1/comissoes/fechamento`: consulta o histórico paginado.

Para editar uma campanha, consultar todas as competências da vigência afetada. A consulta orienta os controles da interface; a operação de salvamento/publicação continua conferindo o bloqueio no momento da escrita. Uma requisição simultânea pode aguardar a trava do fechamento e então ser rejeitada, preservando os dados.

## Como testar

1. Apurar uma competência com uma campanha publicada e conferir o número da versão aplicada, totais e protocolo.
2. Durante uma apuração controlada, consultar o status: deve aparecer `PROCESSANDO` e `bloqueada=true`.
3. Solicitar um segundo fechamento e uma alteração de campanha simultâneos: não pode haver dois fechamentos válidos ou alteração aplicada depois de fechar o período.
4. Provocar falha após uma escrita de cálculo: a escrita deve ser revertida, e a tentativa deve permanecer como `FALHOU`, com protocolo, motivo e competência liberada para repetir.
5. Repetir o fechamento depois de corrigir a causa: manter a tentativa anterior no histórico e concluir uma nova.
6. Em mês fechado, tentar recálculo e cálculo de venda nova pelos caminhos individual, avulso e lote: devem ser rejeitados. Consultas de resultados antigos devem continuar disponíveis.
7. Preparar proposta em outro mês para uma campanha cuja vigência corrente foi fechada: edição/publicação continuam bloqueadas pelo mês corrente fechado.

## Verificação automatizada

Suíte geral em Java 21: 358 testes, 353 aprovados e 5 ignorados, sem falhas ou erros. Os ignorados são o reparo Flyway sem banco, três testes opcionais de migração PostgreSQL e o teste específico do índice parcial.

Execução adicional em PostgreSQL 16 descartável: 9 testes aprovados, sem ignorados — seis verificações de transações/concorrência e três de migração. A inicialização da aplicação nesses testes usa Flyway e valida o esquema com Hibernate. O pacote da aplicação foi gerado com sucesso (`mvn -DskipTests package`). Não houve chamadas à IA ou operações no banco usado pelo usuário.

Os testes de transação podem ser repetidos em PostgreSQL descartável com `FECHAMENTO_TEST_DB_URL`, `FECHAMENTO_TEST_DB_USER` e `FECHAMENTO_TEST_DB_PASSWORD`, executando `mvn -Dtest=FechamentoTransacoesIntegrationTest test`. Sem essas variáveis, usam H2 isolado; o teste específico do índice parcial é ignorado.

Para repetir os testes de migração, definir `MIGRATION_TEST_DB_URL`, `MIGRATION_TEST_DB_USER` e `MIGRATION_TEST_DB_PASSWORD`, executando `mvn -Dtest=HistoricoPostgresMigrationTest test`. Incluem atualização da V24 para V25 preservando código histórico, rastreabilidade, autoria e progresso.

## Integrações seguintes

O frontend ainda precisa integrar os controles de fechamento à edição/publicação e construir comparação/histórico. Permanecem também os contratos de origem e revisão esperada de propostas previstos na próxima tarefa. O #26 é independente e precisará de uma migração com numeração posterior e única.

Esta correção não transforma o fechamento em processamento assíncrono nem adiciona retomada automática após encerramento abrupto do processo. O registro de falha cobre exceções tratadas na apuração; uma interrupção do processo com estado `PROCESSANDO` exige recuperação operacional antes de repetir o fechamento.
