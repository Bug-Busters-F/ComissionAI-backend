# Guia rápido — propostas de edição e histórico

## Contrato adicionado

A campanha expõe três campos adicionais:

```json
{
  "versaoVigente": 1,
  "versaoProposta": 2,
  "versaoOrigem": 1,
  "revisaoControle": 12,
  "motivoBloqueioEdicao": null
}
```

- `revisaoControle`: revisão otimista da campanha, já armazenada em `versao_controle`. Não confundir com o número publicado ou com o número da proposta.
- `versaoOrigem`: versão vigente usada ao iniciar a proposta, armazenada em `versao_anterior_id`. A publicação rejeita uma proposta cuja origem não é mais vigente.
- `motivoBloqueioEdicao`: motivo para consulta apenas, incluindo campanhas encerradas e competências fechadas/em fechamento no período vigente ou proposto. A ausência do motivo não dispensa revalidação nas mutações.

`POST /api/v1/campanhas/{id}/proposta`, com `X-Campanha-Revisao: 12`, copia os dados e todas as regras da versão vigente para uma proposta isolada. Preserva origens, códigos, explicações, pendências e bônus. Não altera a simulação publicada. Se já há proposta, reutiliza-a; rascunhos também a reutilizam.

`PUT /api/v1/campanhas/{id}` e `DELETE /api/v1/campanhas/{id}/proposta` exigem a revisão para campanhas publicadas. Uma revisão antiga retorna **409**, sem aplicar mudanças. O frontend envia a revisão também nos rascunhos; por compatibilidade, ela continua opcional nesses casos. Revisão zero é válida.

Na publicação, enviar ambos:

```http
POST /api/v1/campanhas/42/ativar
X-Campanha-Revisao: 12
Content-Type: application/json

{ "versaoEsperada": 1 }
```

`versaoEsperada` protege a versão publicada e `X-Campanha-Revisao` protege o conteúdo da proposta. A rota alternativa `PATCH /estado` também verifica a revisão ao ativar. O aceite deve corresponder à simulação da proposta atual. O aceite pode alterar a revisão da campanha: consultar novamente antes de publicar.

O progresso agora aceita propostas de campanhas publicadas editáveis, mantendo os mesmos bloqueios de regras e de simulação. O histórico publicado continua imutável e somente para consulta pelas operações de versões existentes. A listagem apresenta os dados vigentes; o detalhe consultado pelo fluxo de edição apresenta a proposta, quando existente. O frontend consulta a versão publicada para apresentar os detalhes vigentes.

## Migração e implantação

A migração **V27** preenche a versão de origem de propostas existentes usando a versão vigente vinculada. Não altera versões publicadas nem redefine revisões. Migrações anteriores não foram modificadas.

Reconstruir/reiniciar o backend desta branch para aplicar V27 e disponibilizar os novos contratos. Preservar o volume do PostgreSQL. A implantação deve acompanhar a branch homônima do frontend; a IA não requer modificações.

Nenhum serviço em uso foi reiniciado durante a implementação. As verificações PostgreSQL e navegador usaram instâncias descartáveis.

## Roteiro de integração

1. Publicar uma campanha inicial. Guardar o detalhe histórico, IDs das regras e simulação publicada.
2. Consultar a campanha, iniciar a proposta com a revisão retornada e conferir `versaoOrigem`.
3. Editar e salvar usando a nova revisão. Conferir que listagem, versão publicada, simulação anterior e cálculos vigentes permanecem preservados.
4. Fazer nova simulação e aceite. Publicar com versão vigente esperada e revisão atualizada. Conferir a nova versão e a preservação da anterior no histórico.
5. Iniciar outra proposta e cancelá-la: a versão vigente e sua simulação devem permanecer disponíveis.
6. Repetir a edição com revisão antiga em salvar, cancelar e publicar: esperar 409 sem perda ou substituição do conteúdo mais recente.
7. Fechar uma competência afetada e consultar `motivoBloqueioEdicao`; tentativas de edição/publicação devem continuar bloqueadas pelas validações transacionais.

## Evidências e limites

Há testes HTTP de cópia isolada, origem, progresso, descarte, preservação do histórico, publicação após novo aceite, conflito de revisão e listagem vigente. O teste PostgreSQL verifica o upgrade V26→V27 e a preservação dos dados publicados.

A conferência no navegador usou backend e PostgreSQL reais descartáveis com campanha sintética e simulação sem vendas. Publicação da revisão e consulta/cópia de Python histórico foram verificadas. A interpretação por Groq não foi acionada nessa conferência; o fluxo com bases e modelo reais deve ser validado no ambiente configurado.

O fluxo existente de cálculo/fechamento e suas limitações operacionais não foram alterados nesta tarefa. Publicar uma revisão não dispara automaticamente uma nova apuração financeira; mantém as regras de cálculo e fechamento já existentes.

## Resultado das verificações desta entrega

- Frontend: 312 testes passaram; build de produção gerado (permanece o aviso já existente de tamanho do pacote).
- Backend: suíte geral com 393 testes, 385 aprovados e 8 condicionais não executados; sem falhas. Testes dirigidos das últimas correções também aprovados.
- PostgreSQL 16: cinco testes de migração aprovados, incluindo V26→V27, em banco descartável.
- Navegador: revisão retomada, comparação vigente/proposta, publicação confirmada e consulta/cópia do Python da versão anterior verificadas com backend real e dados sintéticos.
- A nova proposta de uma campanha publicada abre nas regras existentes (etapa 2), permitindo editar campos sem uma chamada obrigatória à IA. As etapas anteriores continuam acessíveis.
- As alterações estão na branch `feat/edicao-historico-campanhas` nos dois repositórios. Sem commit ou push nesta entrega.
