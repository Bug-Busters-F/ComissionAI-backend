package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.esteira.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.CampanhaEsteiraTransicaoRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CampanhaEsteiraService {

    private static final Logger log = LoggerFactory.getLogger(CampanhaEsteiraService.class);

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final CampanhaEsteiraTransicaoRepository transicaoRepository;
    private final RegraRepository regraRepository;
    private final FechamentoService fechamentoService;
    private final AuditoriaGeralService auditoriaService;

    public CampanhaEsteiraService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            CampanhaEsteiraTransicaoRepository transicaoRepository,
            RegraRepository regraRepository,
            FechamentoService fechamentoService,
            AuditoriaGeralService auditoriaService
    ) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.transicaoRepository = transicaoRepository;
        this.regraRepository = regraRepository;
        this.fechamentoService = fechamentoService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public EstadoEsteiraResponse obterEstadoEsteira(Long campanhaId, Integer versaoNumero) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        return EstadoEsteiraResponse.de(versao);
    }

    @Transactional
    public EstadoEsteiraResponse avancarEtapa(Long campanhaId, Integer versaoNumero, AvancarEsteiraRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        validarImutabilidadeVersao(versao);

        int etapaAlvo = request.etapaAlvo();
        int etapaOrigem = versao.getUltimaEtapa();
        EstadoEsteiraCampanha estadoOrigem = versao.getEstadoEsteira() != null ? versao.getEstadoEsteira() : EstadoEsteiraCampanha.EM_ELABORACAO;

        // Validações de pré-requisitos para cada etapa
        validarPreRequisitosParaAvanco(versao, etapaAlvo);

        // Define o novo estado da esteira
        EstadoEsteiraCampanha novoEstado = switch (etapaAlvo) {
            case 1 -> EstadoEsteiraCampanha.EM_ELABORACAO;
            case 2 -> EstadoEsteiraCampanha.EM_REVISAO_TECNICA;
            case 3 -> EstadoEsteiraCampanha.EM_SIMULACAO;
            case 4 -> EstadoEsteiraCampanha.EM_APROVACAO_GERENCIAL;
            default -> throw new BusinessException("Etapa inválida: " + etapaAlvo);
        };

        // Se estava devolvida, limpa o estado de devolução ao avançar
        if (versao.isDevolvida()) {
            versao.setDevolvidoPor(null);
            versao.setDevolvidoEm(null);
            versao.setMotivoDevolucao(null);
            versao.setEtapaDevolucao(null);
        }

        versao.setUltimaEtapa(etapaAlvo);
        versao.setEtapaMaisAvancada(Math.max(versao.getEtapaMaisAvancada(), etapaAlvo));
        versao.setEstadoEsteira(novoEstado);

        versao = campanhaVersaoRepository.save(versao);

        String responsavel = (request.responsavel() != null && !request.responsavel().isBlank())
                ? request.responsavel() : "analista";

        TipoAcaoEsteira tipoAcao = etapaAlvo > etapaOrigem ? TipoAcaoEsteira.AVANCO_ETAPA : TipoAcaoEsteira.RETORNO_ETAPA;

        CampanhaEsteiraTransicao transicao = new CampanhaEsteiraTransicao(
                versao,
                etapaOrigem,
                etapaAlvo,
                estadoOrigem,
                novoEstado,
                tipoAcao,
                responsavel,
                "Avanço na esteira para etapa " + etapaAlvo
        );
        transicaoRepository.save(transicao);

        if (auditoriaService != null) {
            auditoriaService.registrarAcao(
                    responsavel,
                    TipoAcaoAuditoria.AVANCO_ESTEIRA,
                    TipoEntidadeAuditoria.ESTEIRA,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    String.format("Esteira avançada da etapa %d para %d (%s)", etapaOrigem, etapaAlvo, novoEstado),
                    String.format("{\"etapaOrigem\": %d, \"etapaAlvo\": %d, \"novoEstado\": \"%s\"}", etapaOrigem, etapaAlvo, novoEstado)
            );
        }

        log.info("Campanha {} versão {} avançou da etapa {} para {} (Estado: {}). Responsável: {}",
                campanhaId, versao.getNumero(), etapaOrigem, etapaAlvo, novoEstado, responsavel);

        return EstadoEsteiraResponse.de(versao);
    }

    @Transactional
    public EstadoEsteiraResponse devolverCampanha(Long campanhaId, Integer versaoNumero, DevolverCampanhaRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        validarImutabilidadeVersao(versao);

        int etapaOrigem = versao.getUltimaEtapa();
        int etapaDestino = request.etapaDestino();

        if (etapaDestino >= etapaOrigem) {
            throw new BusinessException(String.format(
                    "A etapa de destino da devolução (%d) deve ser anterior à etapa atual (%d).",
                    etapaDestino, etapaOrigem
            ));
        }

        if (request.motivo() == null || request.motivo().trim().isBlank()) {
            throw new BusinessException("A justificativa formal de devolução é obrigatória.");
        }

        EstadoEsteiraCampanha estadoOrigem = versao.getEstadoEsteira() != null ? versao.getEstadoEsteira() : EstadoEsteiraCampanha.EM_ELABORACAO;

        versao.setEstadoEsteira(EstadoEsteiraCampanha.DEVOLVIDA);
        versao.setDevolvidoPor(request.devolvidoPor());
        versao.setDevolvidoEm(OffsetDateTime.now());
        versao.setMotivoDevolucao(request.motivo().trim());
        versao.setEtapaDevolucao(etapaDestino);
        versao.setUltimaEtapa(etapaDestino);

        // Se devolveu para antes da simulação, invalida aceite gerencial prévio
        versao.setGerenteAprovador(null);
        versao.setGerenteAprovadoEm(null);
        versao.setParecerGerente(null);

        versao = campanhaVersaoRepository.save(versao);

        CampanhaEsteiraTransicao transicao = new CampanhaEsteiraTransicao(
                versao,
                etapaOrigem,
                etapaDestino,
                estadoOrigem,
                EstadoEsteiraCampanha.DEVOLVIDA,
                TipoAcaoEsteira.DEVOLUCAO_GERENCIAL,
                request.devolvidoPor(),
                request.motivo().trim()
        );
        transicaoRepository.save(transicao);

        if (auditoriaService != null) {
            auditoriaService.registrarAcao(
                    request.devolvidoPor(),
                    TipoAcaoAuditoria.DEVOLUCAO_ESTEIRA,
                    TipoEntidadeAuditoria.ESTEIRA,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    String.format("Esteira devolvida da etapa %d para %d. Motivo: %s", etapaOrigem, etapaDestino, request.motivo()),
                    String.format("{\"etapaOrigem\": %d, \"etapaDestino\": %d, \"motivo\": \"%s\"}", etapaOrigem, etapaDestino, request.motivo())
            );
        }

        log.warn("Campanha {} versão {} devolvida pelo gerente {} da etapa {} para etapa {}. Motivo: {}",
                campanhaId, versao.getNumero(), request.devolvidoPor(), etapaOrigem, etapaDestino, request.motivo());

        return EstadoEsteiraResponse.de(versao);
    }

    @Transactional
    public EstadoEsteiraResponse aprovarGerente(Long campanhaId, Integer versaoNumero, AprovarGerenteRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        validarImutabilidadeVersao(versao);

        if (fechamentoService != null) {
            fechamentoService.validarPeriodoDisponivel(versao.getDataInicio(), versao.getDataFim());
        }

        // Valida que o código Python está formalmente aprovado (Etapa 2)
        if (versao.getProgramaPythonAprovado() == null || !versao.getProgramaPythonAprovado().isAprovado()) {
            throw new BusinessException("Aprovação gerencial bloqueada: o programa Python da campanha ainda não possui homologação técnica (Etapa 2).");
        }

        // Valida que a simulação foi formalmente aceita (Etapa 3)
        if (versao.getSimulacaoAceita() == null) {
            throw new BusinessException("Aprovação gerencial bloqueada: a campanha não possui simulação aceita (Etapa 3).");
        }

        EstadoEsteiraCampanha estadoOrigem = versao.getEstadoEsteira() != null ? versao.getEstadoEsteira() : EstadoEsteiraCampanha.EM_ELABORACAO;

        versao.setEstadoEsteira(EstadoEsteiraCampanha.APROVADA);
        versao.setGerenteAprovador(request.aprovadoPor());
        versao.setGerenteAprovadoEm(OffsetDateTime.now());
        versao.setParecerGerente(request.parecer() != null && !request.parecer().isBlank() ? request.parecer().trim() : "Aprovação gerencial concedida.");
        versao.setUltimaEtapa(4);
        versao.setEtapaMaisAvancada(4);

        // Limpa devoluções anteriores
        versao.setDevolvidoPor(null);
        versao.setDevolvidoEm(null);
        versao.setMotivoDevolucao(null);
        versao.setEtapaDevolucao(null);

        versao = campanhaVersaoRepository.save(versao);

        CampanhaEsteiraTransicao transicao = new CampanhaEsteiraTransicao(
                versao,
                4,
                4,
                estadoOrigem,
                EstadoEsteiraCampanha.APROVADA,
                TipoAcaoEsteira.APROVACAO_GERENCIAL,
                request.aprovadoPor(),
                request.parecer() != null ? request.parecer().trim() : "Aprovado pelo gerente."
        );
        transicaoRepository.save(transicao);

        log.info("Campanha {} versão {} aprovada formalmente pelo gerente {}.",
                campanhaId, versao.getNumero(), request.aprovadoPor());

        return EstadoEsteiraResponse.de(versao);
    }

    @Transactional(readOnly = true)
    public List<EsteiraTransicaoResponse> listarHistorico(Long campanhaId, Integer versaoNumero) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        return transicaoRepository.findAllByCampanhaVersaoIdOrderByCriadoEmDesc(versao.getId())
                .stream()
                .map(EsteiraTransicaoResponse::de)
                .toList();
    }

    private void validarPreRequisitosParaAvanco(CampanhaVersao versao, int etapaAlvo) {
        if (etapaAlvo >= 2) {
            List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
            if (regras.isEmpty()) {
                throw new BusinessException("A campanha deve conter pelo menos uma regra antes de avançar para a etapa de interpretação/técnica.");
            }
        }
        if (etapaAlvo >= 3) {
            List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
            boolean temIncompleta = regras.stream().anyMatch(r -> !Boolean.TRUE.equals(r.getCompleta()) || r.getTaxa() == null);
            if (temIncompleta) {
                throw new BusinessException("Corrija as regras incompletas antes de avançar à simulação.");
            }
            if (versao.getProgramaPythonAprovado() == null || !versao.getProgramaPythonAprovado().isAprovado()) {
                throw new BusinessException("O avanço para a Etapa 3 (Simulação) exige a aprovação técnica prévia do programa Python na Etapa 2.");
            }
        }
        if (etapaAlvo == 4) {
            if (versao.getSimulacaoAceita() == null) {
                throw new BusinessException("A conferência gerencial (Etapa 4) exige uma simulação formalmente aceita para a proposta atual.");
            }
        }
    }

    private Campanha buscarCampanha(Long campanhaId) {
        return campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Campanha com ID " + campanhaId + " não encontrada ou removida."));
    }

    private CampanhaVersao resolverVersao(Campanha campanha, Integer versaoNumero) {
        if (versaoNumero != null) {
            return campanhaVersaoRepository.findByCampanhaIdAndNumero(campanha.getId(), versaoNumero)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format("Versão %d da campanha %d não encontrada.", versaoNumero, campanha.getId())));
        }
        CampanhaVersao proposta = campanha.getVersaoProposta();
        if (proposta != null) {
            return proposta;
        }
        CampanhaVersao vigente = campanha.getVersaoVigente();
        if (vigente != null) {
            return vigente;
        }
        return campanhaVersaoRepository.findFirstByCampanhaIdOrderByNumeroDesc(campanha.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma versão encontrada para a campanha " + campanha.getId()));
    }

    private void validarImutabilidadeVersao(CampanhaVersao versao) {
        if (StatusVersaoCampanha.PUBLICADA.equals(versao.getStatus())) {
            throw new BusinessException("Versões já publicadas são imutáveis. Inicie uma nova revisão/proposta para tramitar na esteira.");
        }
    }
}

