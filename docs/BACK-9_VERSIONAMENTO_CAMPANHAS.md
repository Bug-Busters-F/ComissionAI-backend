# BACK-9 — Versionamento de campanhas e regras

## Resumo das alterações

Foi implementada a base de versionamento para que uma campanha publicada não seja alterada enquanto uma revisão estiver em elaboração.

- Criada a entidade `CampanhaVersao`, com os estados `PROPOSTA`, `PUBLICADA` e `CANCELADA`.
- Criada a migração `V21__versionar_campanhas_e_regras.sql`.
  - Cria a tabela `tb_campanha_versao`.
  - Adiciona referências de versão vigente e proposta em `tb_campanha`.
  - Adiciona controle otimista de versão (`versao_controle`).
  - Vincula regras e simulações à versão da campanha a que pertencem.
  - Migra campanhas existentes para uma versão inicial compatível.
- A campanha agora separa:
  - `versaoVigente`: versão publicada utilizada no cálculo produtivo.
  - `versaoProposta`: rascunho que pode ser editado e simulado sem afetar a vigente.
- O endpoint `PUT /api/v1/campanhas/{id}` edita uma proposta. Se a campanha já estiver publicada, a edição cria ou atualiza uma proposta sem modificar a versão vigente.
- A simulação persiste a versão que foi avaliada.
- O aceite de uma simulação publica a proposta na mesma transação:
  1. valida a proposta e suas regras;
  2. promove a proposta para `PUBLICADA`;
  3. ativa as regras da nova versão;
  4. desativa as regras da versão anterior no motor produtivo;
  5. atualiza a referência vigente da campanha;
  6. preserva versões, regras e simulações anteriores para auditoria.
- O payload de aceite aceita `versaoEsperada`, evitando que uma versão seja publicada sobre outra alteração concorrente.
  - Divergência de versão retorna `409 Conflict`.
  - O lock otimista da entidade `Campanha` também protege concorrência no banco.
- Criado `DELETE /api/v1/campanhas/{id}/proposta`, que descarta apenas o rascunho e mantém a versão publicada inalterada.
- A resposta de campanha passou a expor `versaoVigente` e `versaoProposta`.

## Como testar

### Pré-requisito

O projeto exige Java 21. Configure um JDK 21 antes de executar a suíte:

```powershell
cd C:\Users\Autaza\Fatec\apis\api-6\API-6-backend\backend
.\mvnw.cmd test
```

### 1. Publicar a primeira versão

1. Crie uma campanha em `DRAFT` com uma regra de comissão de `0.0500`.
2. Execute uma simulação para a campanha.
3. Confirme a simulação:

```http
POST /api/v1/simulacoes/{simulacaoId}/aceite
Content-Type: application/json

{
  "aceitoPor": "gestor@empresa.com",
  "motivo": "Aprovado"
}
```

Resultado esperado:

- a campanha fica `ATIVA`;
- `versaoVigente` é `1`;
- não há `versaoProposta`;
- as regras da versão 1 estão `ATIVA`.

### 2. Editar sem modificar a versão publicada

Atualize a campanha ativa, mudando a taxa da regra para `0.0600`:

```http
PUT /api/v1/campanhas/{campanhaId}
Content-Type: application/json

{
  "titulo": "Campanha de comissão",
  "textoOriginal": "Comissão revisada para 6%",
  "dataInicio": "2026-06-01",
  "dataFim": "2026-06-30",
  "estado": "DRAFT",
  "regras": [
    {
      "nome": "Comissão revisada",
      "canal": "ECOMMERCE",
      "taxa": 0.0600,
      "completa": true
    }
  ]
}
```

Resultado esperado:

- a campanha continua `ATIVA`;
- `versaoVigente` continua `1`;
- `versaoProposta` passa a ser `2`;
- o cálculo produtivo continua usando 5% até a publicação da proposta.

### 3. Publicar a proposta

Simule a proposta e aceite a simulação informando a versão vigente esperada:

```http
POST /api/v1/simulacoes/{simulacaoIdDaProposta}/aceite
Content-Type: application/json

{
  "aceitoPor": "gestor@empresa.com",
  "motivo": "Aprovada revisão para 6%",
  "versaoEsperada": 1
}
```

Resultado esperado:

- `versaoVigente` passa a ser `2`;
- `versaoProposta` fica nula;
- a campanha continua `ATIVA`;
- regras da versão 2 ficam `ATIVA`;
- regras da versão 1 são preservadas, mas ficam inativas no cálculo produtivo;
- a nova taxa vale para todo o intervalo de vigência da campanha, inclusive vendas anteriores à edição.

### 4. Cancelar uma proposta

Depois de criar uma proposta, cancele-a:

```http
DELETE /api/v1/campanhas/{campanhaId}/proposta
```

Resultado esperado:

- resposta `204 No Content`;
- `versaoProposta` é removida;
- `versaoVigente` permanece inalterada;
- cálculo produtivo continua utilizando a versão vigente.

### 5. Testar concorrência

1. Crie uma proposta sobre a versão 1.
2. Publique outra alteração que passe a vigência para a versão 2.
3. Tente aceitar a primeira proposta com `versaoEsperada: 1`.

Resultado esperado: resposta `409 Conflict`, sem alterar a versão vigente.
