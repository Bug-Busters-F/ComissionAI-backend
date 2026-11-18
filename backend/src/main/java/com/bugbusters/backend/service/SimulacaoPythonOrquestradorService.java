package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.auditoria.AuditoriaEventoContexto;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.programapython.ProgramaPythonResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosRequest;
import com.bugbusters.backend.dto.simulacao.SimulacaoCenariosResponse;
import com.bugbusters.backend.dto.simulacao.SimulacaoPythonRequest;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import com.bugbusters.backend.service.client.AiServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider;

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

        if (versao.getEstadoEsteira() == EstadoEsteiraCampanha.DEVOLVIDA) {
            throw new BusinessException("A versão da campanha está devolvida e precisa ser ajustada antes de executar uma nova simulação.");
        }

        // 1. Resolve e valida integridade SHA-256 do programa Python homologado
        CampanhaProgramaPython programa = resolverProgramaPython(campanhaId, versao, request.versaoPrograma());
        CampanhaProgramaPythonService.validarIntegridadePrograma(programa);
        if (aiServiceClient == null) {
            throw new BusinessException("Cliente do serviço de IA não configurado para executar o programa Python no sandbox.");
        }

        int etapaOrigem = versao.getUltimaEtapa();
        EstadoEsteiraCampanha estadoOrigem = versao.getEstadoEsteira() != null
                ? versao.getEstadoEsteira()
                : EstadoEsteiraCampanha.EM_ELABORACAO;
        String ator = usuarioAtualProvider != null
                ? usuarioAtualProvider.obterUsuarioAtual("orquestrador-python")
                : "orquestrador-python";

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

        // 4. Executa os cálculos no sandbox Python e persiste a simulação
        AtomicReference<AiServiceClient.ExecutarProgramaPythonResponse> pyRespRef = new AtomicReference<>();
        SimulacaoCenariosResponse resp = simulacaoCenariosService.simularEPersistirComMotorExterno(
                cenariosRequest,
                itens -> {
                    List<Map<String, Object>> payloadItens = new ArrayList<>(itens.size());
                    for (SimulacaoCenariosService.VendaCenarioParaCalculo item : itens) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id", item.itemId());
                        entry.put("venda", CampanhaProgramaPythonService.montarVendaParaPython(
                                item.sale(), item.valorEscalado(), item.taxaBaseContratual(), true
                        ));
                        Map<String, Object> ctxItem = new LinkedHashMap<>();
                        ctxItem.put("cenario", item.nomeCenario());
                        ctxItem.put("fator", item.fatorEscala().doubleValue());
                        entry.put("contexto", ctxItem);
                        payloadItens.add(entry);
                    }

                    Map<String, Object> ctxRaiz = new LinkedHashMap<>();
                    ctxRaiz.put("modo", "SIMULACAO");
                    ctxRaiz.put("campanhaId", campanhaId);
                    ctxRaiz.put("versaoCampanha", versao.getNumero());
                    ctxRaiz.put("versaoProgramaPython", programa.getVersaoPrograma());
                    ctxRaiz.put("competencia", request.competencia());

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("codigoFonte", programa.getCodigoFonte());
                    payload.put("contexto", ctxRaiz);
                    payload.put("itens", payloadItens);

                    AiServiceClient.ExecutarProgramaPythonResponse pyResp = aiServiceClient.executarProgramaPython(payload);
                    pyRespRef.set(pyResp);
                    return pyResp.resultados().stream()
                            .collect(Collectors.toMap(
                                    AiServiceClient.ItemExecucaoPython::id,
                                    java.util.function.Function.identity(),
                                    (a, b) -> b
                            ));
                }
        );

        AiServiceClient.ExecutarProgramaPythonResponse pyResp = pyRespRef.get();
        String saidaRaw = serializarSaidaPython(programa, pyResp);

        // 5. Enriquece a simulação persistida com os metadados da execução real no sandbox Python
        if (resp.id() != null) {
            Simulacao sim = simulacaoRepository.findById(resp.id()).orElse(null);
            if (sim != null) {
                sim.setExecutadoViaPython(true);
                sim.setCodigoPythonExecutado(programa.getCodigoFonte());
                sim.setChecksumPython(programa.getChecksumSha256());
                sim.setVersaoProgramaPython(programa.getVersaoPrograma());
                sim.setSaidaPythonRaw(saidaRaw);
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

        EstadoEsteiraCampanha estadoDestino = versao.getEstadoEsteira();

        // 7. Registra transição na esteira
        if (transicaoRepository != null) {
            transicaoRepository.save(new CampanhaEsteiraTransicao(
                    versao,
                    etapaOrigem,
                    versao.getUltimaEtapa(),
                    estadoOrigem,
                    estadoDestino,
                    TipoAcaoEsteira.AVANCO_ETAPA,
                    ator,
                    String.format("Simulação executada no sandbox com programa Python homologado (v%d - %s) para competência %s",
                            programa.getVersaoPrograma(),
                            programa.getChecksumSha256() != null && programa.getChecksumSha256().length() >= 8
                                    ? programa.getChecksumSha256().substring(0, 8) : "hash",
                            request.competencia())
            ));
        }

        if (auditoriaService != null) {
            auditoriaService.registrarEvento(AuditoriaEventoContexto.builder()
                    .usuario(ator)
                    .tipoAcao(TipoAcaoAuditoria.EXECUCAO_SIMULACAO)
                    .tipoEntidade(TipoEntidadeAuditoria.SIMULACAO)
                    .entidadeId(String.valueOf(campanhaId))
                    .versaoEntidade(versao.getNumero())
                    .campanhaId(campanha.getId())
                    .campanhaVersaoId(versao.getId())
                    .campanhaTitulo(campanha.getTitulo())
                    .simulacaoId(resp.id())
                    .protocoloCalculo(resp.protocolo())
                    .competencia(request.competencia())
                    .descricao(String.format("Simulação executada no sandbox Python (programa v%d) com protocolo %s. Vendas: %d, Total Recortado: R$ %s",
                            programa.getVersaoPrograma(), resp.protocolo(), resp.totalVendasRecortadas(), resp.valorTotalRecortado()))
                    .dadosNovos(String.format("{\"protocolo\": \"%s\", \"competencia\": \"%s\", \"vendas\": %d, \"motor\": \"PYTHON_SANDBOX\"}",
                            resp.protocolo(), request.competencia(), resp.totalVendasRecortadas()))
                    .sucesso(true));
        }

        log.info("Simulação executada no sandbox Python (programa v{}) concluída e persistida para campanha {} versão {}. Protocolo: {}",
                programa.getVersaoPrograma(), campanhaId, versao.getNumero(), resp.protocolo());

        // 8. Retorna a resposta completa com os metadados da execução Python
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
                resp.enquadramentoOrcamento(),
                true,
                programa.getVersaoPrograma(),
                programa.getChecksumSha256()
        );
    }

    private String serializarSaidaPython(CampanhaProgramaPython programa, AiServiceClient.ExecutarProgramaPythonResponse pyResp) {
        try {
            Map<String, Object> resumo = new LinkedHashMap<>();
            resumo.put("motor", pyResp != null ? pyResp.motor() : "PYTHON_SANDBOX");
            resumo.put("sandboxConectado", true);
            resumo.put("versaoPrograma", programa.getVersaoPrograma());
            resumo.put("checksum", programa.getChecksumSha256());
            resumo.put("totalItens", pyResp != null ? pyResp.totalItens() : 0);
            resumo.put("sucesso", pyResp == null || pyResp.sucesso());
            return OBJECT_MAPPER.writeValueAsString(resumo);
        } catch (Exception e) {
            return "{\"motor\":\"PYTHON_SANDBOX\",\"sandboxConectado\":true}";
        }
    }

    private CampanhaProgramaPython resolverProgramaPython(Long campanhaId, CampanhaVersao versao, Integer versaoDesejada) {
        CampanhaProgramaPython programa;
        if (versaoDesejada != null) {
            programa = programaPythonRepository.findByCampanhaVersaoIdAndVersaoPrograma(versao.getId(), versaoDesejada)
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(
                            "Versão %d do programa Python não encontrada para a versão %d da campanha %d.",
                            versaoDesejada, versao.getNumero(), campanhaId
                    )));
        } else if (versao.getProgramaPythonAprovado() != null) {
            programa = versao.getProgramaPythonAprovado();
        } else {
            programa = programaPythonRepository.findFirstByCampanhaVersaoIdOrderByVersaoProgramaDesc(versao.getId())
                    .orElseThrow(() -> new BusinessException(
                            "Nenhum programa Python homologado e aprovado foi encontrado para esta campanha/versão. Homologue um programa antes de simular."
                    ));
        }

        if (!programa.isAprovado()) {
            throw new BusinessException(String.format(
                    "O programa Python v%d não está aprovado tecnicamente (status atual: %s). Aprove-o antes de executar a simulação.",
                    programa.getVersaoPrograma(),
                    programa.getStatusAprovacao()
            ));
        }
        return programa;
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

