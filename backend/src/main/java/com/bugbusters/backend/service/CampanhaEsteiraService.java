package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.esteira.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.CampanhaEsteiraTransicaoRepository;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.repository.CampanhaVersaoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.SimulacaoRepository;
import com.bugbusters.backend.dto.pendencia.ResumoPendenciasCampanhaResponse;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class CampanhaEsteiraService {

    private static final Logger log = LoggerFactory.getLogger(CampanhaEsteiraService.class);

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final CampanhaEsteiraTransicaoRepository transicaoRepository;
    private final RegraRepository regraRepository;
    private final FechamentoService fechamentoService;
    private final AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private CampanhaPendenciaService pendenciaService;

    @Autowired(required = false)
    private SimulacaoRepository simulacaoRepository;

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider = (paliativo) -> (paliativo != null && !paliativo.isBlank()) ? paliativo.trim() : "SISTEMA";

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

        if (etapaAlvo > etapaOrigem + 1) {
            throw new BusinessException(String.format(
                    "Transição inválida na esteira: as etapas devem ser avançadas sequencialmente (atual: %d, alvo solicitada: %d).",
                    etapaOrigem, etapaAlvo
            ));
        }

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

        String responsavel = usuarioAtualProvider.obterUsuarioAtual(
                (request.responsavel() != null && !request.responsavel().isBlank()) ? request.responsavel() : "analista");

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

        EstadoEsteiraCampanha estadoOrigem = versao.getEstadoEsteira() != null ? versao.getEstadoEsteira() : EstadoEsteiraCampanha.EM_ELABORACAO;
        int etapaOrigem = versao.getUltimaEtapa() != null ? versao.getUltimaEtapa() : 1;
        boolean temSimulacaoAceita = versao.getSimulacaoAceita() != null || campanha.getSimulacaoAceita() != null;
        boolean encaminhadaAoGerente = versao.isEncaminhadaGerente()
                || campanha.isEncaminhadaGerente()
                || EstadoEsteiraCampanha.EM_APROVACAO_GERENCIAL.equals(estadoOrigem);
        int etapaEfetivaOrigem = encaminhadaAoGerente
                ? Math.max(etapaOrigem, 4)
                : (temSimulacaoAceita ? Math.max(etapaOrigem, 3)
                : ((versao.getProgramaPythonAprovado() != null || versao.getStatusAprovacaoCodigo() == StatusAprovacaoCodigo.APROVADO)
                        ? Math.max(etapaOrigem, 2) : etapaOrigem));
        int etapaDestino = request.etapaDestino();

        if (etapaDestino >= etapaEfetivaOrigem) {
            throw new BusinessException(String.format(
                    "A etapa de destino da devolução (%d) deve ser anterior à etapa atual (%d).",
                    etapaDestino, etapaOrigem
            ));
        }

        if (request.motivo() == null || request.motivo().trim().isBlank()) {
            throw new BusinessException("A justificativa formal de devolução é obrigatória.");
        }
        String motivoLimpo = request.motivo().trim();
        String devolvidoPor = usuarioAtualProvider.obterUsuarioAtual(request.devolvidoPor());
        OffsetDateTime agora = OffsetDateTime.now();

        versao.setEstadoEsteira(EstadoEsteiraCampanha.DEVOLVIDA);
        versao.setDevolvidoPor(devolvidoPor);
        versao.setDevolvidoEm(agora);
        versao.setMotivoDevolucao(motivoLimpo);
        versao.setEtapaDevolucao(etapaDestino);
        versao.setUltimaEtapa(etapaDestino);

        // Limpa encaminhamento gerencial e aprovações prévias (FRONT-9 / BACK-4)
        versao.setGerenteAprovador(null);
        versao.setGerenteAprovadoEm(null);
        versao.setParecerGerente(null);
        versao.setEncaminhadaGerente(false);
        versao.setEncaminhadaGerentePor(null);
        versao.setEncaminhadaGerenteEm(null);
        versao.setObservacaoEncaminhamento(null);

        // Invalida aceite de simulação vinculado à versão devolvida
        if (simulacaoRepository != null) {
            List<Simulacao> simulacoes = simulacaoRepository.findAllByCampanhaVersaoId(versao.getId());
            if ((simulacoes == null || simulacoes.isEmpty()) && campanha.getVersaoVigente() == null) {
                simulacoes = simulacaoRepository.findAllByCampanhaIdAndStatusAceite(campanha.getId(), StatusAceiteSimulacao.ACEITA);
            }
            if (simulacoes != null && !simulacoes.isEmpty()) {
                for (Simulacao sim : simulacoes) {
                    if (sim.getStatusAceite() == StatusAceiteSimulacao.ACEITA || sim.getStatusAceite() == StatusAceiteSimulacao.PENDENTE) {
                        sim.setStatusAceite(StatusAceiteSimulacao.INVALIDADA);
                        sim.setInvalidadoEm(agora);
                        sim.setMotivoInvalidacao("Campanha devolvida pelo gerente: " + motivoLimpo);
                    }
                    if (campanha.getSimulacaoAceita() != null && Objects.equals(campanha.getSimulacaoAceita().getId(), sim.getId())) {
                        campanha.setSimulacaoAceita(null);
                    }
                }
                simulacaoRepository.saveAll(simulacoes);
            }
        }
        versao.setSimulacaoAceita(null);
        if (campanha.getVersaoVigente() == null) {
            campanha.setSimulacaoAceita(null);
        }

        // Quando devolvida para revisão de regras ou código (etapa <= 2), invalida homologação técnica e código
        if (etapaDestino <= 2) {
            versao.setProgramaPythonAprovado(null);
            if (versao.getStatusAprovacaoCodigo() == StatusAprovacaoCodigo.APROVADO) {
                versao.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);
                versao.setCodigoInvalidadoEm(agora);
                versao.setMotivoInvalidacaoCodigo("Código invalidado após devolução para a Etapa " + etapaDestino + ": " + motivoLimpo);
            }
            List<Regra> regrasVersao = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
            List<Regra> regrasInvalidar = regrasVersao.stream()
                    .filter(r -> r.getStatusAprovacaoCodigo() == StatusAprovacaoCodigo.APROVADO)
                    .toList();
            if (!regrasInvalidar.isEmpty()) {
                for (Regra regra : regrasInvalidar) {
                    regra.setStatusAprovacaoCodigo(StatusAprovacaoCodigo.INVALIDADO);
                    regra.setCodigoInvalidadoEm(agora);
                    regra.setMotivoInvalidacaoCodigo("Código invalidado após devolução para a Etapa " + etapaDestino + ": " + motivoLimpo);
                }
                regraRepository.saveAll(regrasInvalidar);
            }
        }

        versao = campanhaVersaoRepository.save(versao);

        if (Objects.equals(versao.getId(), campanha.getVersaoProposta() != null ? campanha.getVersaoProposta().getId() : null)
                || campanha.getVersaoVigente() == null) {
            campanha.setEncaminhadaGerente(false);
            campanha.setEncaminhadaGerentePor(null);
            campanha.setEncaminhadaGerenteEm(null);
            campanha.setObservacaoEncaminhamento(null);
            if (campanha.getVersaoVigente() == null && campanha.getEstado() != EstadoCampanha.DRAFT) {
                campanha.setEstado(EstadoCampanha.DRAFT);
            }
            campanha.setAtualizadoEm(agora);
            campanhaRepository.save(campanha);
        }

        CampanhaEsteiraTransicao transicao = new CampanhaEsteiraTransicao(
                versao,
                etapaOrigem,
                etapaDestino,
                estadoOrigem,
                EstadoEsteiraCampanha.DEVOLVIDA,
                TipoAcaoEsteira.DEVOLUCAO_GERENCIAL,
                devolvidoPor,
                motivoLimpo
        );
        transicaoRepository.save(transicao);

        if (auditoriaService != null) {
            String motivoEscapado = motivoLimpo
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
            auditoriaService.registrarAcao(
                    devolvidoPor,
                    TipoAcaoAuditoria.DEVOLUCAO_ESTEIRA,
                    TipoEntidadeAuditoria.ESTEIRA,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    String.format("Esteira devolvida da etapa %d para %d. Motivo: %s", etapaOrigem, etapaDestino, motivoLimpo),
                    String.format("{\"etapaOrigem\": %d, \"etapaDestino\": %d, \"motivo\": \"%s\"}", etapaOrigem, etapaDestino, motivoEscapado)
            );
        }

        log.warn("Campanha {} versão {} devolvida pelo gerente {} da etapa {} para etapa {}. Motivo: {}",
                campanhaId, versao.getNumero(), devolvidoPor, etapaOrigem, etapaDestino, motivoLimpo);

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

        // Valida pendências impeditivas em aberto (BUG-102)
        validarPendenciasAbertasParaEsteira(versao, "aprovação gerencial");

        String aprovadoPor = usuarioAtualProvider.obterUsuarioAtual(request.aprovadoPor());
        if (aprovadoPor == null || aprovadoPor.isBlank() || ("SISTEMA".equals(aprovadoPor) && (request.aprovadoPor() == null || request.aprovadoPor().isBlank()))) {
            throw new BusinessException("O identificador do gerente aprovador é obrigatório.");
        }

        int etapaOrigem = versao.getUltimaEtapa();
        EstadoEsteiraCampanha estadoOrigem = versao.getEstadoEsteira() != null ? versao.getEstadoEsteira() : EstadoEsteiraCampanha.EM_ELABORACAO;

        versao.setEstadoEsteira(EstadoEsteiraCampanha.APROVADA);
        versao.setGerenteAprovador(aprovadoPor);
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
                etapaOrigem,
                4,
                estadoOrigem,
                EstadoEsteiraCampanha.APROVADA,
                TipoAcaoEsteira.APROVACAO_GERENCIAL,
                aprovadoPor,
                request.parecer() != null ? request.parecer().trim() : "Aprovado pelo gerente."
        );
        transicaoRepository.save(transicao);

        log.info("Campanha {} versão {} aprovada formalmente pelo gerente {}.",
                campanhaId, versao.getNumero(), aprovadoPor);

        return EstadoEsteiraResponse.de(versao);
    }

    @Transactional(readOnly = true)
    public List<EsteiraTransicaoResponse> listarHistorico(Long campanhaId, Integer versaoNumero) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);
        return transicaoRepository.findAllByCampanhaVersaoIdOrderByCriadoEmDescIdDesc(versao.getId())
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
            validarPendenciasAbertasParaEsteira(versao, "avanço para a Etapa " + etapaAlvo);
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

    private void validarPendenciasAbertasParaEsteira(CampanhaVersao versao, String operacao) {
        if (pendenciaService == null || versao.getCampanha() == null) {
            return;
        }
        ResumoPendenciasCampanhaResponse resumo = pendenciaService.listarPendencias(versao.getCampanha().getId(), versao.getNumero());
        if (resumo != null && !resumo.aptaParaAvanco()) {
            throw new BusinessException(String.format(
                    "Bloqueio de %s: existem %d pendência(s) impeditiva(s) sem aceite formal registrado.",
                    operacao, resumo.totalPendentesImpeditivas()
            ));
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

