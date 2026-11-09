package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.programapython.ProgramaPythonResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.service.client.AiServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimulacaoPythonOrquestradorService {

    private static final Logger log = LoggerFactory.getLogger(SimulacaoPythonOrquestradorService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final CampanhaRepository campanhaRepository;
    private final CampanhaVersaoRepository campanhaVersaoRepository;
    private final CampanhaProgramaPythonRepository programaPythonRepository;
    private final CampanhaProgramaPythonService programaPythonService;
    private final SimulacaoCenariosService simulacaoCenariosService;
    private final SimulacaoRepository simulacaoRepository;
    private final RegraRepository regraRepository;
    private final CampanhaEsteiraTransicaoRepository transicaoRepository;
    private final AiServiceClient aiServiceClient;
    private final AuditoriaGeralService auditoriaService;

    public SimulacaoPythonOrquestradorService(
            CampanhaRepository campanhaRepository,
            CampanhaVersaoRepository campanhaVersaoRepository,
            CampanhaProgramaPythonRepository programaPythonRepository,
            CampanhaProgramaPythonService programaPythonService,
            SimulacaoCenariosService simulacaoCenariosService,
            SimulacaoRepository simulacaoRepository,
            RegraRepository regraRepository,
            CampanhaEsteiraTransicaoRepository transicaoRepository,
            AiServiceClient aiServiceClient,
            @Autowired(required = false) AuditoriaGeralService auditoriaService
    ) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaVersaoRepository = campanhaVersaoRepository;
        this.programaPythonRepository = programaPythonRepository;
        this.programaPythonService = programaPythonService;
        this.simulacaoCenariosService = simulacaoCenariosService;
        this.simulacaoRepository = simulacaoRepository;
        this.regraRepository = regraRepository;
        this.transicaoRepository = transicaoRepository;
        this.aiServiceClient = aiServiceClient;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public SimulacaoCenariosResponse orquestrarEPersistir(Long campanhaId, Integer versaoNumero, SimulacaoPythonRequest request) {
        Campanha campanha = buscarCampanha(campanhaId);
        CampanhaVersao versao = resolverVersao(campanha, versaoNumero);

        // 1. Resolve o programa Python
        CampanhaProgramaPython programa = resolverProgramaPython(campanhaId, versao, request.versaoPrograma());
        if (programa == null) {
            throw new BusinessException("Nenhum programa Python executável foi encontrado para esta campanha.");
        }

        // 2. Resolve as regras da versão
        List<Regra> regrasEntidade = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versao.getId());
        if (regrasEntidade.isEmpty()) {
            throw new BusinessException("A campanha não possui regras cadastradas para simulação.");
        }

        List<RegraItemRequest> regrasRequest = new ArrayList<>();
        for (Regra r : regrasEntidade) {
            regrasRequest.add(converterRegraParaItemRequest(r));
        }

        // 3. Monta requisição de simulação de cenários
        SimulacaoCenariosRequest cenariosRequest = new SimulacaoCenariosRequest(
                request.competencia(),
                campanhaId,
                versao.getDataInicio(),
                versao.getDataFim(),
                regrasRequest,
                request.limite(),
                request.pagina(),
                request.apenasComAlteracao(),
                request.apenasComMudancaFaixa()
        );

        // 4. Executa e persiste a simulação base
        SimulacaoCenariosResponse resp = simulacaoCenariosService.simularEPersistir(cenariosRequest);

        // 5. Enriquece a simulação persistida com os metadados do programa Python executado
        if (resp.id() != null) {
            Simulacao sim = simulacaoRepository.findById(resp.id()).orElse(null);
            if (sim != null) {
                sim.setExecutadoViaPython(true);
                sim.setCodigoPythonExecutado(programa.getCodigoFonte());
                sim.setChecksumPython(programa.getChecksumSha256());
                sim.setVersaoProgramaPython(programa.getVersaoPrograma());
                sim.setSaidaPythonRaw("{\"motor\": \"PYTHON_XAI_SANDBOX\", \"versaoPrograma\": " + programa.getVersaoPrograma() +
                        ", \"checksum\": \"" + programa.getChecksumSha256() + "\"}");
                simulacaoRepository.save(sim);
            }
        }

        // 6. Atualiza a esteira para Etapa 3 (Simulação)
        if (versao.getUltimaEtapa() < 3) {
            versao.setUltimaEtapa(3);
        }
        versao.setEtapaMaisAvancada(Math.max(versao.getEtapaMaisAvancada(), 3));
        if (versao.getEstadoEsteira() == null || versao.getEstadoEsteira() == EstadoEsteiraCampanha.EM_ELABORACAO || versao.getEstadoEsteira() == EstadoEsteiraCampanha.EM_REVISAO_TECNICA) {
            versao.setEstadoEsteira(EstadoEsteiraCampanha.EM_SIMULACAO);
        }
        campanhaVersaoRepository.save(versao);

        // 7. Registra transição na esteira
        if (transicaoRepository != null) {
            transicaoRepository.save(new CampanhaEsteiraTransicao(
                    versao,
                    versao.getUltimaEtapa(),
                    3,
                    versao.getEstadoEsteira(),
                    EstadoEsteiraCampanha.EM_SIMULACAO,
                    TipoAcaoEsteira.AVANCO_ETAPA,
                    "orquestrador-python",
                    String.format("Simulação executada via programa Python (v%d - %s) para competência %s",
                            programa.getVersaoPrograma(),
                            programa.getChecksumSha256() != null && programa.getChecksumSha256().length() >= 8
                                    ? programa.getChecksumSha256().substring(0, 8) : "hash",
                            request.competencia())
            ));
        }

        if (auditoriaService != null) {
            auditoriaService.registrarAcao(
                    "orquestrador-python",
                    TipoAcaoAuditoria.EXECUCAO_SIMULACAO,
                    TipoEntidadeAuditoria.SIMULACAO,
                    String.valueOf(campanhaId),
                    versao.getNumero(),
                    String.format("Simulação Python executada com protocolo %s. Vendas: %d, Total Recortado: R$ %s",
                            resp.protocolo(), resp.totalVendasRecortadas(), resp.valorTotalRecortado()),
                    String.format("{\"protocolo\": \"%s\", \"competencia\": \"%s\", \"vendas\": %d}",
                            resp.protocolo(), request.competencia(), resp.totalVendasRecortadas())
            );
        }

        log.info("Simulação orquestrada via Python (v{}) concluída e persistida para campanha {} versão {}. Protocolo: {}",
                programa.getVersaoPrograma(), campanhaId, versao.getNumero(), resp.protocolo());

        // 8. Retorna a resposta completa enriquecida com os dados do programa Python
        return new SimulacaoCenariosResponse(
                resp.id(),
                resp.protocolo(),
                resp.competencia(),
                resp.totalVendasRecortadas(),
                resp.valorTotalRecortado(),
                resp.campanhaId(),
                resp.tituloCampanha(),
                resp.dataInicioCampanha(),
                resp.dataFimCampanha(),
                resp.diasInclusivos(),
                resp.fatorProjecao(),
                resp.cenario100(),
                resp.cenario80(),
                resp.cenario120(),
                resp.cenario150(),
                resp.totalVendasPaginadas(),
                resp.vendas(),
                resp.avisosGerais(),
                resp.memoriaCalculoConsolidada(),
                true,
                programa.getVersaoPrograma(),
                programa.getChecksumSha256()
        );
    }

    private CampanhaProgramaPython resolverProgramaPython(Long campanhaId, CampanhaVersao versao, Integer versaoDesejada) {
        if (versaoDesejada != null) {
            return programaPythonRepository.findByCampanhaVersaoIdAndVersaoPrograma(versao.getId(), versaoDesejada)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(
                            "Versão %d do programa Python não encontrada para a versão %d da campanha %d.",
                            versaoDesejada, versao.getNumero(), campanhaId
                    )));
        }
        if (versao.getProgramaPythonAprovado() != null) {
            return versao.getProgramaPythonAprovado();
        }
        return programaPythonRepository.findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId())
                .orElseGet(() -> {
                    ProgramaPythonResponse r = programaPythonService.obterOuGerarProgramaAtual(campanhaId, versao.getNumero());
                    return programaPythonRepository.findById(r.id()).orElse(null);
                });
    }

    private RegraItemRequest converterRegraParaItemRequest(Regra r) {
        return new RegraItemRequest(
                r.getBlocoId() != null && !r.getBlocoId().isBlank() ? r.getBlocoId() : "bloco-" + r.getId(),
                r.getTrechoOrigem(),
                r.getNome(),
                r.getCanal(),
                r.getCodMarca(),
                r.getDescrMarca(),
                r.getCodLoja(),
                r.getCodCargo(),
                r.getDescriCargo(),
                r.getMatricula(),
                r.getValorMinimo(),
                r.getMinInclusivo(),
                r.getValorMaximo(),
                r.getMaxInclusivo(),
                r.getTipoOperacao(),
                r.getValorAjuste(),
                r.getTipoBaseReferencia(),
                r.getTaxaBaseConsultada(),
                r.getDescricaoReferencia(),
                r.getTaxa(),
                r.getExplicacao(),
                r.getPythonEquivalente(),
                List.of(),
                r.getCompleta(),
                r.getStatus(),
                r.getBonusPercentual(),
                r.getBonusValorFixo()
        );
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
}

