# Orçamento informado no texto da campanha

## Correção

A extração de orçamento do interpretador aceita conectivos comuns, incluindo “orçamento total para a campanha é R$130000” e “orçamento da campanha é de R$ 130.000,00”. Números sem separador de milhar são consumidos integralmente, evitando ler somente `130` de `130000`. Mantido o uso de BigDecimal e dos multiplicadores mil, milhão e milhões. Valores negativos, zero e formatos inválidos não preenchem um orçamento parcial.

O caminho API do frontend já transportava o orçamento recebido para o rascunho; foram adicionados testes para proteger o salvamento, recarregamento e preenchimento na etapa 3. A confirmação do orçamento continua explícita antes da execução. Não há alteração na IA nem mudança de contrato.

## Verificação

Use no texto:

> Em dezembro de 2025 todos da marca VERMELHO recebem 5% de comissão. O orçamento total para a campanha é R$130000.

Após interpretar, o rascunho deve conter `orcamento` e `orcamentoExtraidoIa` iguais a 130000, com `orcamentoConfirmado=false`. Na etapa 3, o campo deve aparecer preenchido com `130000,00`. Clique em “Confirmar orçamento” antes de simular. Recarregar a campanha deve preservar o valor salvo.

Campanhas interpretadas antes da correção precisam de nova interpretação para extrair o valor do texto, ou preenchimento manual do orçamento. Não há alteração automática de campanhas existentes.

## Execução e testes

Reconstrua e reinicie apenas o backend; não remova o volume do banco.

- Backend: `InterpretadorOrcamentoTextoTest` e `InterpretadorOrcamentoTest`: 20 testes passaram, usando a extração real e respostas controladas da IA.
- Frontend: testes de fluxo verificam persistência e recarga; teste da tela verifica o campo preenchido e a necessidade de confirmação.

Nenhuma chamada real ao provedor de IA é necessária para esses testes.
