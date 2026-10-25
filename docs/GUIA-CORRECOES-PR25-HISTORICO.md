# Guia rápido — correções do PR #25

Branch: `feat/versions-historic`, atualizada com a `dev` em `398fcf4`. Alterações somente no backend e sua documentação. Frontend, IA e banco em uso não foram alterados.

## Entrega

- Migração nova passa a V24, preservando as migrações V22 e V23 existentes.
- Migração considera a publicação anterior, ignorando lacunas de versões canceladas; associa autoria e simulação somente à versão publicada correspondente. O aceite de uma proposta não contamina o histórico da vigente.
- Consultas históricas expõem `regras[].rastreabilidade` do conteúdo salvo, junto da explicação e Python da própria versão. Nenhum artefato histórico é regenerado na consulta.
- Comparação inclui rastreabilidade e situação completa, além dos campos existentes. Identificadores duplicados não podem causar descarte silencioso de regras.
- Parâmetros obrigatórios ausentes ou inválidos recebem 400 em vez de 500.
- Limpeza de fixtures respeita referências de versão/simulação e versão anterior. Testes transacionais usam seu rollback, sem excluir globalmente simulações de publicações históricas.
- Documentação distingue aceite da simulação e publicação na confirmação final.

## Operações reutilizadas

- `GET /api/v1/campanhas/{id}/versoes`
- `GET /api/v1/campanhas/{id}/versoes/{numero}`
- `GET /api/v1/campanhas/{id}/versoes/comparacao?versaoBase=1&versaoComparada=2`

O detalhe acrescenta `rastreabilidade` por regra. Nenhum campo já usado pelo frontend foi removido. Registros legados sem rastreabilidade retornam `null`.

## Como verificar

1. Criar, simular, aceitar e ativar uma campanha. O aceite sozinho não deve publicar.
2. Consultar versão 1 e conferir autoria, protocolo, campos, origens, explicação e Python.
3. Editar uma proposta, alterar taxa/faixas/filtros, adicionar e remover regras. Simular, aceitar e publicar enviando `versaoEsperada` na ativação.
4. Consultar novamente a versão 1: os valores e artefatos antigos devem permanecer. Comparar com a nova publicação e conferir inclusões, remoções e alterações.
5. Cancelar uma proposta intermediária e publicar outra: a versão anterior deve apontar para a última publicada, sem incluir a cancelada no histórico.
6. Tentar consultar uma proposta pela rota histórica: resposta 404. Enviar parâmetros ausentes/inválidos: 400. Enviar identificadores de bloco duplicados: 400, preservando a versão vigente.

## Testes e migrações

Suíte geral executada em Java 21: 318 testes, 315 aprovados e 3 ignorados, sem falhas ou erros. Os ignorados são o reparo Flyway sem banco e os dois testes opcionais de PostgreSQL.

O pacote da aplicação foi gerado com sucesso (`mvn -DskipTests package`).

Os dois testes `HistoricoPostgresMigrationTest` foram executados separadamente e passaram com PostgreSQL 16 descartável: instalação do zero e atualização a partir da V23 com dados, histórico, progresso, código e rastreabilidade existentes. Também verificam autoria da própria versão e proteção contra atribuição do aceite de uma proposta à vigente. Não foi usado o banco do usuário.

Para repetir em uma base PostgreSQL de testes, definir `MIGRATION_TEST_DB_URL`, `MIGRATION_TEST_DB_USER` e `MIGRATION_TEST_DB_PASSWORD`, e executar `mvn -Dtest=HistoricoPostgresMigrationTest test`. Os testes criam schemas exclusivos; não executam clean nem repair.

## Integrações seguintes

Este PR pode fornecer histórico de publicações depois do push e da aprovação. Ainda não entrega a interface de edição nem comparação de uma proposta não publicada. Essas etapas precisam dos controles de origem da proposta, revisão esperada no salvamento/cancelamento e progresso de propostas de campanhas aprovadas. Os bloqueios de fechamento serão tratados no PR #27.

O PR #26 continua independente; a migração dele e a do #27 precisam de números únicos depois da V24. Não renomear migrações que já foram aplicadas no ambiente.
