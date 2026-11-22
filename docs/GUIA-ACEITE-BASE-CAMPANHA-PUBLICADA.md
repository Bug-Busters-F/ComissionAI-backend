# Aceite de advertências das bases em campanhas publicadas

## Operações

Mantidos os contratos existentes:
- `POST /api/v1/campanhas/{id}/pendencias/aceite` com `inconsistenciaImportacaoId`, `severidade: AVISO` e justificativa.
- `POST /api/v1/campanhas/{id}/pendencias/{aceiteId}/revogar` com justificativa.
- Consulta paginada de `/api/v1/campanhas/{id}/cobertura-fechamento/pendencias` apresenta o estado persistido.

## Comportamento

Para uma inconsistência de importação, o serviço busca a fonte e verifica advertência, vínculo ativo, competência dentro da vigência, arquivo disponível, permissões e fechamento. O aceite usa a versão vigente quando há uma campanha publicada, mesmo com proposta de edição em andamento. Campo, mensagem, identificador e severidade vêm da inconsistência original; responsável vem da sessão.

Somente gerente ou administrador pode registrar/revogar o aceite das bases. Uma competência fechada ou em fechamento permanece bloqueada. Aceites de pendências das regras publicadas continuam proibidos. Aceitar ou revogar uma advertência de arquivo não recalcula resultados e não modifica regras, explicações ou código aprovado.

A consulta usa os aceites da versão vigente, sem recorrer aos de uma proposta ou de uma versão histórica quando a vigente não possui aceites.

## Verificação

Testes de serviço cobrem severidade real, origem, competência, vínculo, identidade, permissões e revogação. Testes HTTP com banco isolado verificam persistência, idempotência sequencial, consulta de cobertura, revogação e preservação da publicação/programa. Não registram decisões de risco nas campanhas locais.

Para testar manualmente, abra uma campanha publicada de competência aberta, confira a advertência de uma base vinculada e registre uma justificativa fundamentada. Confira a persistência após recarregar e o diagnóstico antes de apurar. Outros requisitos das bases continuam obrigatórios.

Verificação automatizada: 42 testes selecionados passaram (serviço de aceite, HTTP com persistência, pendências, preparação, cobertura e fechamento). A validação manual do aceite real permanece com o usuário.

## Execução

Reconstruir e reiniciar somente o serviço backend. Não exige migração, exclusão de volumes ou reinicialização dos dados.
