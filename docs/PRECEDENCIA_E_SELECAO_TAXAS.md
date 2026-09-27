# Política de Precedência e Seleção de Regras e Taxas (S1-B08)

Este documento descreve as regras de negócio, a hierarquia de precedência determinística e os critérios de resolução de taxa de comissão implementados no componente `TaxaComissaoResolver` para a Sprint 1 do **ComissionAI**.

---

## 1. Visão Geral e Fontes de Taxa

O sistema avalia duas fontes potenciais de taxa para cada venda:

1. **Regras de Negócio e Campanhas Promocionais (`tb_regra`):**
   - Regras temporárias cadastradas por gestores com critérios de vigência, canal, loja, marca, cargo ou vendedor.
   - Status considerado: exclusivamente **`ATIVA`** (regras em `DRAFT`, `PENDENTE_APROVACAO` ou `INATIVA` são desconsideradas).
   - Regras com data de exclusão lógica (`removido_em IS NOT NULL`) são sumariamente ignoradas.

2. **Taxa Base Padrão (`tb_basecomiss`):**
   - Taxas contratuais importadas por Marca e Cargo para o mês/ano de competência (`sale_date.withDayOfMonth(1)`).
   - Serve de fallback contratual padrão quando não há campanha ativa aplicável.

---

## 2. Validação de Vínculos Essenciais (Pré-requisitos)

Antes de buscar qualquer percentual, o motor valida os vínculos e a consistência da venda. Caso algum falhe, o cálculo é marcado como **`IMPEDIDO`** sem consulta de taxas:

- **Colaborador / Matrícula (`Registration`):** Deve existir e estar vinculado à venda.
- **Cargo (`Position`):** O colaborador deve possuir um cargo válido associado.
- **Marca (`Brand`):** A venda deve ter uma marca vinculada.
- **Valor da Venda:** Deve ser estritamente positivo ($> 0$).
- **Vigência Contratual:** A data da venda (`sale_date`) não pode ser anterior à data de admissão (`admiss_date`) nem posterior à data de demissão (`demiss_date`) do colaborador.

---

## 3. Matriz de Precedência Explícita

O motor aplica a seguinte ordem determinística:

### Prioridade 1: Regra de Negócio Ativa (`tb_regra`)
- Se houver regra ativa aplicável aos critérios da venda na data especificada, ela **substitui integralmente** a taxa base da competência.
- **Origem registrada:** `"REGRA_NEGOCIO"`.

### Prioridade 2: Taxa Base Contratual (`tb_basecomiss`)
- Caso nenhuma regra ativa seja encontrada, busca a taxa padrão para a combinação `Marca + Cargo` no mês da venda (ou mês mais recente disponível).
- **Origem registrada:** `"BASE_COMISS"`.

> **Regra Fundamental de Isolamento:** Percentuais **NUNCA são somados**. O motor seleciona estritamente **uma única taxa**.

---

## 4. Resolução de Múltiplas Regras e Bloqueio de Conflitos

Quando mais de uma regra ativa coincide com a mesma venda, o motor aplica uma pontuação de especificidade determinística:

| Critério Atendido na Regra | Peso de Especificidade |
| :--- | :---: |
| Matrícula específica (`matricula`) | **16** |
| Loja específica (`cod_loja`) | **8** |
| Cargo específico (`cod_cargo`) | **4** |
| Canal específico (`canal != 'PADRAO'`) | **2** |
| Marca geral (`cod_marca`) | **1** |

### Regra de Decisão:
1. **Regra Mais Específica Vence:** Se uma regra possuir pontuação estritamente superior às demais (ex: regra por colaborador individual sobrepondo regra geral de loja), ela prevalece deterministicamente.
2. **Bloqueio de Ambiguidade / Conflito:** Se houver empate na maior pontuação com múltiplas regras ativas concorrentes (ex: duas regras para a mesma loja com taxas divergentes), o motor **NÃO escolhe aleatoriamente**. Ele **bloqueia o cálculo** e emite o impedimento:
   ```
   Conflito de regras: múltiplas regras ativas concorrentes encontradas com o mesmo nível de especificidade (Regras IDs: [101, 102]). Resolução bloqueada por ambiguidade.
   ```

---

## 5. Rastreabilidade e Auditoria

Toda seleção bem-sucedida ou impedida gera um resultado rastreável (`ResolucaoTaxaResult`), contendo:
- `sucesso`: indicativo booleano de aptidão para cálculo.
- `taxa`: valor decimal `BigDecimal` com escala 4 (`RoundingMode.HALF_UP`).
- `idRegra`: identificador da regra aplicada (ou ID 1 para BaseComiss).
- `origemTaxa`: `"REGRA_NEGOCIO"` ou `"BASE_COMISS"`.
- `motivoImpedimento`: justificativa contratual caso a venda não possa ser calculada.
