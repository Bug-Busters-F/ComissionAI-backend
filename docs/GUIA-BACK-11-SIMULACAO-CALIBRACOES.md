# BACK-11 — Simulação Python, alternativas e aplicação ao rascunho

## Escopo e dependências

Implementação somente no backend, em `feat/simulacao-python-calibracoes`, a partir da dev `a8ffb6c`. Depende das operações `/api/v2/executar-programa` e `/api/v2/simular` do PR #18 da IA (`374e913`). O frontend continua acessando apenas o backend. Aplique a migração V46 na próxima reconstrução; não apague o banco nem volumes.

A simulação principal executa o código exato atualmente aprovado para a proposta. Confere a assinatura SHA-256 devolvida pela IA, a quantidade e os identificadores de todos os itens, duplicidades, comissões ausentes/negativas e o uso explícito do fallback contratual. Uma falha nessa execução não produz resultado persistido parcial ou sucesso fictício.

## Contrato para FRONT-7 e FRONT-8

A revisão do programa continua usando os endpoints já existentes de `programa-python`: consulta, histórico, atualização, aprovação e rejeição. Alterar o código invalida simulações e aceite da proposta e exige revisão novamente.

### Executar

`POST /api/v1/campanhas/{id}/simulacao-python` ou `/api/v1/campanhas/{id}/versoes/{numero}/simulacao-python`:

```json
{
  "competencia": "2025-07",
  "versaoPrograma": 1,
  "limite": 50,
  "pagina": 0,
  "calibrar": true,
  "solicitacaoId": "4facb9ab-eacb-4dc2-a22a-88827d307f47"
}
```

Os campos anteriores permanecem compatíveis. `calibrar` é opcional e false por padrão. O orçamento precisa ter sido informado/confirmado pelo fluxo existente. O teto não é alterado pela calibração. `solicitacaoId` é opcional, mas o frontend deve gerar um UUID por execução intencional e reutilizá-lo em tentativas da mesma requisição. Uma repetição confirmada retorna a simulação já persistida; alterações nos parâmetros ou fontes exigem uma nova chave e podem produzir 409. Execuções que falham antes da confirmação são revertidas e podem ser repetidas.

A operação é síncrona: não apresenta progresso percentual por item nem cria um novo job assíncrono. O frontend deve indicar processamento e tratar timeout consultando o histórico antes de iniciar uma nova execução. Com a mesma chave, chamadas concorrentes da mesma campanha são serializadas, sem duplicação.

### Resposta

A resposta existente de cenários recebe `analise`:

- `execucaoId`: ID da simulação persistida.
- `versaoCampanha`, `versaoPrograma`, `assinaturaPrograma`: origem da execução.
- `orcamento`: teto utilizado no snapshot.
- `statusSugestoes`: `NAO_SOLICITADA`, `OK` ou `FALHA`.
- `motivo`: explicação quando a busca/conferência não pode ser realizada.
- `versoesFontes`: hashes das vendas efetivamente enviadas, regras, recorte e programa.
- `calibracoes[]`: quatro cenários, com `cenario` (`80%`, `100%`, `120%`, `150%`), status, custo original, custo recalculado, folga, assinatura candidata e alterações.
- Cada alteração contém `regraId` persistido (numérico), `campo` (`taxa`, `bonus.percentual`, `bonus.valorFixo`), `antes` e `depois`. Taxas são frações: `0.0350` significa 3,5%.
- `explicacoes[]`: resumos determinísticos por cenário, valores numéricos utilizados e limitações. Não são textos inventados pelo LLM; correspondem aos totais do programa aprovado e aos custos das alternativas verificadas.

Os estados por alternativa são `NAO_NECESSARIA`, `ENQUADRADA`, `SEM_SOLUCAO_VIAVEL` e `FALHA`. Somente `ENQUADRADA` permite aplicação. Uma falha na busca de alternativas preserva a simulação principal e suas explicações; não deve aparecer como "sem solução" nem como sucesso da calibração.

A IA sugere parâmetros. Antes de oferecer uma alternativa enquadrada, o backend gera o candidato com as mesmas condições do programa da campanha e reexecuta seus itens no sandbox. Confere a comissão total antes da projeção e recalcula o custo projetado com o mesmo fator da simulação. Isso evita que diferenças de precisão do fator de 31/30 ou um motor diferente sejam apresentados como custos confirmados.

### Recuperar após recarregar

- `GET /api/v1/simulacoes/{id}` inclui a mesma `analise` persistida.
- `GET /api/v1/simulacoes/{id}/analise-python` retorna apenas o snapshot.
- Simulações anteriores à migração continuam consultáveis; nelas `analise` é null e a consulta específica retorna 404, orientando uma nova simulação. Não se fabricam artefatos históricos.

### Aplicar mediante confirmação

`POST /api/v1/simulacoes/{id}/calibracoes/aplicar`:

```json
{
  "cenario": "100%",
  "revisaoEsperada": 7,
  "assinaturaProgramaEsperada": "sha256-devolvido-na-analise"
}
```

`revisaoEsperada` corresponde à revisão de controle atual da campanha, não ao número da versão publicada. O frontend deve consultar o estado atual antes de confirmar e não reenviar livremente valores de taxas ou custos: o servidor aplica somente a alternativa persistida/verificada.

A resposta é o novo `ProgramaPythonResponse`, com situação `PENDENTE_REVISAO`. A aplicação altera somente taxa/bônus do rascunho; preserva filtros, condições, vigência e orçamento. Os valores originais são conferidos, a proposta e o programa precisam corresponder à origem, e períodos fechados são bloqueados. Aplicações repetidas, propostas concorrentes ou snapshots desatualizados retornam 409/erro de negócio sem alterações parciais.

A nova intenção é encaminhada para a etapa 2, com novas revisão e simulação obrigatórias. A aprovação anterior, simulações/aceites da proposta e encaminhamento gerencial são invalidados. Dados e resultados publicados permanecem preservados. Explicação e Python por regra alterada deixam de ser apresentados como atuais; as origens dos outros campos são mantidas. O evento de aplicação registra autoria da sessão e origem da alternativa na auditoria.

Consulta está disponível aos cinco perfis autenticados. Aplicação está restrita a administrador e analista de negócios pela autorização do servidor; gerente, analista de código e auditor não aplicam parâmetros.

## Limitações explícitas

- A IA do PR #18 calibra regras parametrizadas em um template, e não código arbitrário `calcular_comissao`. Por isso o backend só oferece calibração automática quando o programa aprovado corresponde ao gerador atual e usa definição direta de taxa. Para código personalizado ou operações sobre a base, retorna `FALHA` com orientação de ajuste/revisão manual. A execução original desses programas permanece disponível: eles não são trocados por outro template.
- Operações sobre a base e código livre poderão receber calibração automática quando a IA disponibilizar parâmetros elegíveis e reexecução segura do mesmo contrato. Isso não pode ser inferido genericamente de qualquer Python pelo backend.
- Uma alternativa pode ficar abaixo da taxa contratual, conforme o comportamento atual da IA e a decisão de negócio ainda pendente sobre um piso. O orçamento e as condições de elegibilidade permanecem fixos.
- A calibração não efetiva mudanças sem confirmação nem aprova o programa candidato automaticamente.
- Não foram alterados frontend, IA, arquivos de ambiente, banco ou contêineres de execução do usuário nesta implementação. A instância temporária da IA usada nos testes não usa LLM e não altera o repositório.

## Verificação

Os testes cobrem persistência após limpar a sessão JPA, repetição sem duplicidade, quatro cenários, validação de custos, precisão, falhas de IA, respostas incompletas, campos não elegíveis, ausência de orçamento, programa personalizado, aplicação/reaplicação e necessidade de nova revisão. A integração opt-in `contratoRealComIaPr18QuandoConfigurado` usa `-Dback11.ia-url=http://...` contra uma instância isolada do PR #18 e valida uma vigência de 31 dias.

### Resultados em 20/11/2026

- Suíte completa no estado final: **561 testes, 552 aprovados, 9 ignorados, nenhuma falha ou erro**, em Java 21/H2. Um dos ignorados é a integração opt-in com a IA; os demais são os cenários já ignorados pelo projeto.
- Rodada específica com a IA real do PR #18: **25 testes aprovados**, sem ignorados. Foram executados o programa completo e a geração de sugestões, seguidos de três reexecuções de candidatos no sandbox. A vigência de 31 dias foi validada, sem usar LLM ou outro executor no lugar do Python.
- Pacote de produção gerado com sucesso.
- A migração de calibrações (originalmente V45, renumerada para V46 em 22/11/2026) foi aplicada com sucesso em PostgreSQL 16 descartável, sobre uma cópia somente da estrutura V44. Nenhum dado do banco local foi alterado. O contêiner temporário foi removido.
- Instância temporária da IA encerrada após o teste; o serviço habitual do usuário não foi substituído.
- `git diff --check` aprovado.
- Registros locais de teste em `/tmp/comissionai-back11-*`; não são arquivos da aplicação.

A integração manual nas telas fica para a implementação da FRONT-7/FRONT-8. Antes de usar os novos endpoints no ambiente habitual, integrar/atualizar a IA com o PR #18 e reconstruir o backend para aplicar V46, preservando os volumes.


## Correção da numeração após integração na dev (22/11/2026)

A migração de alertas do outro PR mantém `V45__blindar_unicidade_alertas_e_corrigir_dados_legados.sql`. Nossa migração passa a ser `V46__persistir_analise_calibracoes_simulacao.sql`, executada por último. Seu conteúdo SQL foi preservado integralmente; somente o nome mudou.

Bancos novos ou que tenham chegado até V44 executam V45 e V46 normalmente. Bancos que já executaram a V45 de alertas também recebem V46 normalmente.

**Ambientes que aplicaram a antiga V45 de calibrações antes da integração:** a simples renomeação do arquivo não altera o histórico que já está no banco. Antes de reconstruir/iniciar o backend, faça backup e confira `flyway_schema_history`. Somente se a linha de versão 45 corresponder ao script `V45__persistir_analise_calibracoes_simulacao.sql`, estiver bem-sucedida, não houver versão 46 e as cinco alterações de estrutura estiverem presentes, a versão e o nome desse registro podem ser reconciliados para 46 e `V46__persistir_analise_calibracoes_simulacao.sql`, preservando checksum, descrição e demais campos. O conteúdo não mudou, portanto o checksum deve permanecer o mesmo.

O projeto já usa `spring.flyway.out-of-order: true`; após essa reconciliação específica, o Flyway poderá executar a V45 de alertas que ainda estiver pendente, sem reaplicar as alterações de calibrações da V46. Não use `repair` genérico para atribuir ao script de alertas o histórico de outro script, não altere checksums arbitrariamente e não apague tabelas nem volumes para resolver a colisão. A reconciliação depende da inspeção do banco de cada ambiente.

Validação da correção: Flyway real e PostgreSQL 16 descartável aprovaram três caminhos — banco novo até V46, banco com V45 de alertas seguido de V46, e banco com antiga V45 de calibrações após reconciliação específica do histórico seguido da V45 de alertas fora de ordem. Os três concluíram `validate()` sem erros; a estrutura esperada foi conferida e um registro de controle permaneceu preservado no caminho legado. O banco do usuário não foi alterado. Faça uma compilação limpa (`mvn clean package` ou a reconstrução Docker, cujo Dockerfile já usa `clean`) para não manter o antigo arquivo V45 em `target/classes`.
