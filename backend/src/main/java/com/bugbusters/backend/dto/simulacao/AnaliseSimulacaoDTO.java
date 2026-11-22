package com.bugbusters.backend.dto.simulacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Snapshot imutável dos números e alternativas vinculados à execução, nunca à página de vendas. */
public record AnaliseSimulacaoDTO(UUID execucaoId, Integer versaoCampanha, Integer versaoPrograma,
        String assinaturaPrograma, BigDecimal orcamento, String statusSugestoes, String motivo,
        List<Calibracao> calibracoes, List<Explicacao> explicacoes, Map<String,String> versoesFontes) {
    public record Alteracao(Long regraId, String campo, BigDecimal antes, BigDecimal depois) {}
    public record Calibracao(String cenario, String status, BigDecimal custoProjetadoOriginal,
            BigDecimal custoProjetadoRecalculado, BigDecimal folga, List<Alteracao> alteracoes,
            String assinaturaCandidata, String motivo) {}
    public record Explicacao(String cenario, String resumo, Map<String, BigDecimal> numeros,
            List<String> limitacoes) {}
}
