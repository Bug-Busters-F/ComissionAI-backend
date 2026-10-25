package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.*;
import com.bugbusters.backend.dto.interpretador.proposta.*;
import com.bugbusters.backend.model.Regra;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bugbusters.backend.exception.BusinessException;

public final class RastreabilidadeRegraService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ArtefatoExplicativoService ARTEFATOS = new ArtefatoExplicativoService();
    private RastreabilidadeRegraService() {}

    public static void aplicar(Regra regra, RastreabilidadeRegraDTO evidence) {
        if (evidence == null) return; // Contrato legado: não atribuir novas evidências.
        // Canonicalize exactly as the database and commission engine, before describing the values.
        if (regra.getTaxa() != null) regra.setTaxa(regra.getTaxa().setScale(4, java.math.RoundingMode.HALF_UP));
        if (regra.getValorAjuste() != null) regra.setValorAjuste(regra.getValorAjuste().setScale(4, java.math.RoundingMode.HALF_UP));
        if (regra.getBonusPercentual() != null) regra.setBonusPercentual(regra.getBonusPercentual().setScale(4, java.math.RoundingMode.HALF_UP));
        if (regra.getBonusValorFixo() != null) regra.setBonusValorFixo(regra.getBonusValorFixo().setScale(2, java.math.RoundingMode.HALF_UP));
        if (regra.getValorMinimo() != null) regra.setValorMinimo(regra.getValorMinimo().setScale(2, java.math.RoundingMode.HALF_UP));
        if (regra.getValorMaximo() != null) regra.setValorMaximo(regra.getValorMaximo().setScale(2, java.math.RoundingMode.HALF_UP));
        boolean atual = Boolean.TRUE.equals(evidence.artefatosAtualizados());
        PropostaRegraDTO proposta = new PropostaRegraDTO(regra.getBlocoId(), regra.getTrechoOrigem(),
            new FiltrosRegraDTO(regra.getCanal(), regra.getCodMarca(), regra.getDescrMarca(), regra.getCodLoja(), regra.getCodCargo(), regra.getDescriCargo(), regra.getMatricula()),
            new FaixaValorDTO(regra.getValorMinimo(), regra.getMinInclusivo(), regra.getValorMaximo(), regra.getMaxInclusivo()),
            new OperacaoBaseDTO(regra.getTipoOperacao(), regra.getValorAjuste(),
                new ReferenciaBaseDTO(regra.getTipoBaseReferencia(), regra.getTaxaBaseConsultada(), regra.getDescricaoReferencia())),
            regra.getTaxa(), evidence.referenciasConsultadas(), evidence.origensPorCampo(), evidence.pendencias().stream().map(PendenciaRastreabilidadeDTO::paraProposta).toList(),
            regra.getCompleta(), null, null, new PeriodoVigenciaDTO(regra.getDataInicio(), regra.getDataFim()), regra.getBonusPercentual(), regra.getBonusValorFixo());
        if (atual && ARTEFATOS.validarProposta(proposta).stream().anyMatch(PendenciaPropostaDTO::isImpeditiva)) {
            throw new BusinessException("Os campos atuais da regra não permitem gerar artefatos válidos. Corrija e valide novamente.");
        }
        if (atual) {
            var calculada = ARTEFATOS.calcularTaxaEfetiva(proposta.operacaoBase());
            if (calculada == null || regra.getTaxa() == null || calculada.compareTo(regra.getTaxa()) != 0)
                throw new BusinessException("A taxa atual diverge da operação sobre a base. Atualize a explicação e confirme o percentual antes de salvar.");
        }
        boolean bloqueada = java.util.stream.Stream.concat(evidence.pendencias().stream(), evidence.pendenciasCampanha().stream())
                .anyMatch(p -> p.severidade() != SeveridadePendencia.AVISO);
        var f = proposta.filtros();
        boolean semPublico = f.canal() == null && f.codMarca() == null && f.descrMarca() == null && f.codCargo() == null && f.descriCargo() == null && f.codLoja() == null && f.matricula() == null;
        if (!atual || bloqueada || (semPublico && !Boolean.TRUE.equals(evidence.publicoTodasVendas()))) regra.setCompleta(false);
        regra.setExplicacao(atual ? ARTEFATOS.gerarExplicacao(proposta) : null);
        regra.setPythonEquivalente(atual ? ARTEFATOS.gerarPythonEquivalente(proposta) : null);
        try { regra.setRastreabilidadeJson(JSON.writeValueAsString(evidence)); }
        catch (Exception e) { throw new BusinessException("Não foi possível preservar a rastreabilidade da regra."); }
    }
    public static RastreabilidadeRegraDTO ler(Regra regra) {
        if (regra.getRastreabilidadeJson() == null) return null;
        try { return JSON.readValue(regra.getRastreabilidadeJson(), RastreabilidadeRegraDTO.class); }
        catch (Exception e) { throw new IllegalStateException("Rastreabilidade persistida inválida para regra " + regra.getId(), e); }
    }
}
