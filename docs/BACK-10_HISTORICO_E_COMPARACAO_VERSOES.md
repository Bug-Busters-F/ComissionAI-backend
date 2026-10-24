# BACK-10 — Histórico, autoria e comparação entre versões

## Alterações implementadas

- A versão publicada da campanha agora registra:
  - número da versão anterior;
  - responsável pelo aceite/publicação, informado pela aplicação;
  - data e hora de publicação;
  - simulação aceita e seu protocolo.
- Foi incluído um identificador lógico estável em cada regra. O `blocoId` é usado quando disponível; regras legadas recebem uma chave compatível durante a migração.
- Foi criada a migração `V22__historico_autoria_e_comparacao_versoes.sql`, que adiciona os metadados de histórico e preenche referências existentes quando possível.
- Na publicação de uma proposta, a aplicação associa a versão anterior, o responsável e a simulação aceita diretamente à versão publicada.
- Foram disponibilizadas consultas somente para versões publicadas:
  - `GET /api/v1/campanhas/{id}/versoes`: lista o histórico resumido;
  - `GET /api/v1/campanhas/{id}/versoes/{numero}`: obtém o conteúdo imutável publicado;
  - `GET /api/v1/campanhas/{id}/versoes/comparacao?versaoBase={n}&versaoComparada={n}`: compara duas versões.
- O detalhe histórico inclui conteúdo da campanha e das regras: filtros, faixa, operação, referências/origens, taxa, explicação, Python equivalente, pendências e bônus.
- A comparação identifica regras `ADICIONADA`, `REMOVIDA`, `ALTERADA` ou `SEM_ALTERACAO`, usando o identificador lógico, e aponta os campos alterados com os valores anterior e atual.
- Propostas ainda não publicadas não são retornadas pelos endpoints de histórico.

## Como testar

### Pré-requisito

O projeto requer Java 21:

```powershell
cd C:\Users\Autaza\Fatec\apis\api-6\API-6-backend\backend
.\mvnw.cmd test
```

### 1. Gerar duas versões publicadas

1. Publique a versão 1 de uma campanha com uma regra de comissão de 5%.
2. Edite a campanha, mantendo o mesmo `blocoId` da regra e alterando a taxa para 6%.
3. Simule e publique a proposta como versão 2.

### 2. Consultar o histórico

```http
GET /api/v1/campanhas/{campanhaId}/versoes
```

Resultado esperado: versões 2 e 1, com número anterior, responsável, data de publicação, ID da simulação aceita e protocolo.

### 3. Consultar versão imutável

```http
GET /api/v1/campanhas/{campanhaId}/versoes/1
```

Resultado esperado: taxa de 5%, artefatos explicativos e Python que pertenciam à versão 1, mesmo depois de a versão 2 estar vigente.

```http
GET /api/v1/campanhas/{campanhaId}/versoes/2
```

Resultado esperado: taxa de 6% e os metadados da publicação da versão 2.

### 4. Comparar versões

```http
GET /api/v1/campanhas/{campanhaId}/versoes/comparacao?versaoBase=1&versaoComparada=2
```

Resultado esperado para uma alteração de taxa:

```json
{
  "versaoBase": 1,
  "versaoComparada": 2,
  "alteracoesRegras": [
    {
      "identificadorLogico": "bloco-1",
      "tipo": "ALTERADA",
      "alteracoes": [
        {
          "campo": "taxa",
          "anterior": 0.0500,
          "atual": 0.0600
        }
      ]
    }
  ]
}
```

### 5. Testar inclusão e remoção de regra

1. Publique uma nova versão adicionando uma regra com novo `blocoId`.
2. Compare com a versão anterior: a regra deve aparecer como `ADICIONADA`.
3. Publique outra versão sem essa regra.
4. Compare novamente: ela deve aparecer como `REMOVIDA`.
