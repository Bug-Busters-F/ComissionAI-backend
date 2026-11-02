package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.model.LotePreparacao;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.repository.SimulacaoVendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Implementação padrão de BACK-5 para verificação de impactos.
 * Bloqueia alterações com 409 Conflict se houver cálculos, logs financeiros ou simulações dependentes dos registros.
 * Na BACK-6, será estendida para suportar prévia e propagação automática.
 */
@Component
public class BloqueadorImpactoCorrecaoBasePadrao implements ImpactoCorrecaoBaseHandler {

    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final SimulacaoVendaRepository simulacaoVendaRepository;

    public BloqueadorImpactoCorrecaoBasePadrao(
            @Autowired(required = false) ResultadoCalculoRepository resultadoCalculoRepository,
            @Autowired(required = false) LogCalculoRepository logCalculoRepository,
            @Autowired(required = false) SimulacaoVendaRepository simulacaoVendaRepository) {
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.simulacaoVendaRepository = simulacaoVendaRepository;
    }

    @Override
    public void validarImpactoRh(LotePreparacao lotePreparacao, Collection<String> matriculasAlteradas) {
        if (matriculasAlteradas == null || matriculasAlteradas.isEmpty()) {
            return;
        }

        List<CampoInvalidoDTO> impedimentos = new ArrayList<>();

        if (resultadoCalculoRepository != null && resultadoCalculoRepository.existsByMatriculaIn(matriculasAlteradas)) {
            impedimentos.add(new CampoInvalidoDTO(
                    "calculos",
                    "Existem cálculos oficiais consolidados dependentes das matrículas alteradas: " + matriculasAlteradas
            ));
        }

        if (logCalculoRepository != null && logCalculoRepository.existsByMatriculaIn(matriculasAlteradas)) {
            impedimentos.add(new CampoInvalidoDTO(
                    "logs_financeiros",
                    "Existem logs de auditoria financeira vinculados às matrículas alteradas: " + matriculasAlteradas
            ));
        }

        if (!impedimentos.isEmpty()) {
            throw new ResourceConflictException(
                    "Operação de correção bloqueada: as matrículas que sofreriam alteração possuem dependências ativas em produção.",
                    impedimentos
            );
        }
    }

    @Override
    public void validarImpactoVendas(LotePreparacao lotePreparacao, Collection<UUID> idsVendasAlteradas) {
        if (idsVendasAlteradas == null || idsVendasAlteradas.isEmpty()) {
            return;
        }

        List<CampoInvalidoDTO> impedimentos = new ArrayList<>();

        if (resultadoCalculoRepository != null && resultadoCalculoRepository.existsByIdVendaIn(idsVendasAlteradas)) {
            impedimentos.add(new CampoInvalidoDTO(
                    "calculos",
                    "Existem cálculos oficiais vinculados às vendas que sofreriam alteração."
            ));
        }

        if (logCalculoRepository != null && logCalculoRepository.existsByIdVendaIn(idsVendasAlteradas)) {
            impedimentos.add(new CampoInvalidoDTO(
                    "logs_financeiros",
                    "Existem logs de auditoria financeira vinculados às vendas que sofreriam alteração."
            ));
        }

        if (simulacaoVendaRepository != null && simulacaoVendaRepository.existsByIdVendaIn(idsVendasAlteradas)) {
            impedimentos.add(new CampoInvalidoDTO(
                    "simulacoes",
                    "Existem simulações ativas vinculadas às vendas que sofreriam alteração."
            ));
        }

        if (!impedimentos.isEmpty()) {
            throw new ResourceConflictException(
                    "Operação de correção bloqueada: as vendas que sofreriam alteração possuem cálculos ou logs dependentes.",
                    impedimentos
            );
        }
    }
}
