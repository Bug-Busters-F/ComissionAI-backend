# Guia rápido — backend da revisão rastreável

Branch: `feat/revisao-rastreabilidade-campanhas`, baseada na `dev` `2146716`.
Usar com a branch de mesmo nome no frontend. A IA continua na `dev` `1bc472d`, sem alterações.

## Implantação local

Reconstruir/reiniciar o backend com a branch atual. O Flyway aplica `V23__rastreabilidade_e_progresso_campanhas.sql`, adicionando:

- `tb_regra.rastreabilidade_json` (TEXT, opcional para registros legados).
- `tb_campanha_versao.ultima_etapa` e `etapa_mais_avancada`, de 1 a 4.

Não é necessário apagar ou recriar o banco existente. Os serviços habituais em execução não foram substituídos pelos testes desta entrega.

## Contrato adicional das regras

POST/PUT `/api/v1/campanhas` aceita `rastreabilidade` em cada regra; GET devolve o mesmo objeto:

```json
{
  "origensPorCampo": { "canal": "TEXTO", "taxa": "EDICAO_MANUAL" },
  "referenciasConsultadas": [],
  "camposManuais": ["taxaPercentual"],
  "pendencias": [],
  "pendenciasCampanha": [],
  "publicoTodasVendas": false,
  "artefatosAtualizados": true
}
```

Cada pendência preserva `id`, `campo`, `codigo`, `mensagem`, `severidade` (`AVISO` ou `IMPEDIMENTO`), `origem`, `escopo`, `exigeValidacaoRemota` e `camposRelacionados`. A cobertura de validação não se perde ao recarregar. As mensagens simples de `pendencias` permanecem para compatibilidade.

As origens existentes são mantidas; o enum também aceita `EDICAO_MANUAL`, `OPERACAO_CALCULADA` e `BASE_COMISSAO`. Ausência de rastreabilidade em dados legados é devolvida como `null`, sem preencher evidências fictícias.

Quando a nova rastreabilidade informa artefatos atuais, o backend valida a operação/taxa e regenera explicação e Python a partir dos campos que realmente serão persistidos, da vigência comum e dos bônus. Assim, código antigo enviado junto com valores novos não é reaproveitado. Valores são normalizados para a escala persistida; o Python usa `Decimal` e `ROUND_HALF_UP`, com comissão e bônus arredondados separadamente como no motor.

Quando os artefatos estão desatualizados, explicação e Python não são mantidos como atuais e a regra fica incompleta. Impedimentos estruturados ou público genérico sem confirmação também mantêm a regra incompleta. Rascunhos incompletos continuam podendo ser salvos.

## Atualização dos artefatos

`POST /api/v1/interpretador/atualizar-artefatos` mantém `{ proposta, camposEditados }`. Os campos editados usam o contrato, por exemplo `taxa`, `filtros.codLoja`, `condicaoValor.valorMinimo`.

A resposta adiciona `operacaoBase`, `origensPorCampo` e `referenciasConsultadas`, permitindo ao frontend atualizar a referência da operação e a rastreabilidade atual. A taxa manual conserva `DEFINIR_TAXA`. O Python retornado é gerado deterministicamente no backend; a explicação segue a integração existente com alternativa local quando o serviço de artefatos está indisponível.

## Progresso do rascunho

GET da campanha adiciona `ultimaEtapa` e `etapaMaisAvancada`, associadas à versão exibida. Etapas: 1 proposta, 2 interpretação, 3 simulação, 4 conferência.

PATCH `/api/v1/campanhas/{id}/progresso`:

```json
{ "ultimaEtapa": 2, "etapaMaisAvancada": 3, "versaoProposta": 1 }
```

- Restrito a DRAFT com proposta correspondente; uma versão diferente retorna conflito.
- A simulação exige regras completas e a conferência exige aceite válido da proposta atual.
- O servidor aumenta o maior avanço apenas a partir da etapa efetivamente visitada, sem confiar no maior contador enviado pelo cliente.
- Navegar não substitui regras, não atualiza o cabeçalho de negócio e não invalida a simulação aceita. A assinatura canônica da simulação permanece a existente.

## Verificações e limites

309 testes executados, sem falhas/erros, um teste preexistente ignorado. Testes novos cobrem persistência das evidências, descarte de artefatos antigos, progresso, restrições de etapas/versão/estado. O teste do ciclo de múltiplas regras também confirma que a gravação da etapa 4 preserva o aceite e permite a ativação posterior.

Migração até V23 e validação Hibernate conferidas em PostgreSQL isolado. O Python gerado foi executado separadamente em seis cenários de precisão, bônus, elegibilidade e vigência. Navegação/revisão também conferidas no navegador com frontend e backend reais em H2 isolado.

Não foi realizado novo teste de interpretação com a Groq. Limites do provedor, completude das evidências retornadas pela IA e enriquecimento futuro da rastreabilidade continuam dependendo do serviço de IA. O Python é uma representação individual para conferência; não substitui resolução de concorrência, precedência e execução do motor de campanhas.
