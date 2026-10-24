# Guia rápido — correções do PR #23

Atualização em 24/10/2026 na branch `feat/campaing-versions`. A dev foi atualizada para `a87f8c8` (inclui o merge do PR #24) e integrada à branch local do #23 sem conflitos. Este guia acompanha as correções do PR #23 e descreve o contrato preservado para a primeira ativação e os requisitos para publicar revisões.

## Comportamento corrigido

- Aceitar a simulação registra o aceite e mantém a proposta em revisão. A publicação/ativação acontece somente na confirmação final.
- Aceite e ativação conferem campanha, versão, vigência e assinatura das regras simuladas. Simulações invalidadas/rejeitadas não podem ser aceitas novamente.
- Editar uma proposta invalida suas simulações, inclusive as ainda pendentes, preservando os aceites históricos de versões publicadas. Consultar a conferência nunca cria um aceite automático.
- Cancelar uma proposta preserva a versão vigente, restaura seu vínculo de aceite e invalida as simulações da proposta cancelada. A próxima revisão usa o maior número histórico mais um: v1 publicada → v2 cancelada → v3 proposta.
- Cabeçalho, vigência, regras e simulação usam a mesma proposta em revisão. Os campos persistidos da versão vigente permanecem preservados até a publicação.
- O estado das regras acompanha o ciclo de vida da campanha. Enviar `status: ATIVA` dentro de uma regra de rascunho não a coloca no motor de produção.
- Inativar uma campanha com proposta pendente inativa as regras vigentes e mantém a proposta como rascunho.

## Integração com o frontend atual

A primeira publicação mantém os endpoints e a sequência já usada pelo formulário multirregras:

1. Criar/salvar a campanha em DRAFT.
2. Executar `POST /api/v1/simulacoes` com a campanha persistida.
3. Registrar `POST /api/v1/simulacoes/{id}/aceite` com responsável e motivo. A campanha continua DRAFT e suas regras continuam DRAFT.
4. Conferir `GET /api/v1/campanhas/{id}/conferencia-simulacao`.
5. Após a revisão, confirmar `POST /api/v1/campanhas/{id}/ativar`, sem corpo ou com `{}`. A versão 1 torna-se vigente e suas regras passam a ATIVA.

O frontend não precisa antecipar a ativação para aceitar a simulação. A integração da IA incorporada pelo PR #24 foi preservada.

## Revisões de campanhas já publicadas

O fluxo de edição com versionamento no frontend ainda é uma entrega própria. Para integrar essa interface posteriormente:

- `PUT /api/v1/campanhas/{id}` salva a proposta, mantendo o estado ATIVA e a versão vigente preservada. `versaoVigente` e `versaoProposta` identificam os números. Enquanto existir proposta, os campos e regras do detalhe representam a proposta.
- Simulação e aceite pertencem à proposta atual. Aceitar não substitui as regras de produção.
- A ativação final exige o número vigente que o usuário confirmou:

```http
POST /api/v1/campanhas/123/ativar
Content-Type: application/json

{"versaoEsperada":1}
```

- Ausência ou divergência de `versaoEsperada` na publicação de uma revisão retorna 409. Recarregar os dados e solicitar nova conferência; não repetir silenciosamente.
- O mesmo campo é aceito em `PATCH /api/v1/campanhas/{id}/estado` ao publicar uma revisão. A versão opcional do payload de aceite continua servindo para detectar divergências, mas não publica a campanha.
- `DELETE /api/v1/campanhas/{id}/proposta` cancela somente a proposta. Reinterpretar, simular e revisar novamente antes de tentar publicar outra revisão.

## Migração

A V21 existente foi mantida. A nova `V22__vincular_simulacoes_legadas_a_versao_inicial.sql` associa as simulações anteriores à versão inicial criada na V21. Ela preserva os snapshots, hashes e status de aceite; não concede novos aceites.

O Flyway aplica a migração ao iniciar o backend atualizado. O banco real e o serviço em uso não foram reiniciados nem migrados nesta execução.

## Verificação

- Java 21/Maven, suíte completa: **305 testes, zero falhas, zero erros, um ignorado**. Assim, 304 testes efetivamente executados passaram.
- Cobertura inclui criação multirregras → simulação → aceite → ativação, proposta alterada depois de simular, ausência de aceite automático, revisão sem afetar a vigente, cancelamento seguido de nova revisão, versão esperada incorreta e proteção contra status ATIVA em rascunho.
- O motor de resolução de taxas foi verificado com a versão vigente em 5% durante a edição/aceite da proposta de 6%, e em 6% somente após a ativação final.
- Os três testes de exclusão que já falhavam na dev foram isolados dos registros deixados por outros testes, sem remover as validações de dependências do código de produção.
- Preparação/limpeza das fixtures foi adaptada às relações entre campanhas, versões, regras e simulações.
- V21 + V22 verificadas em PostgreSQL 16 isolado com campanha ativa e rascunho preexistentes. Confirmadas referências de versão, preservação de hash e manutenção do aceite original. Este teste não aplica o histórico inteiro de migrações nem modifica o banco em uso.
- Nenhuma chamada real à Groq foi necessária. Frontend e IA não foram alterados nesta correção.
