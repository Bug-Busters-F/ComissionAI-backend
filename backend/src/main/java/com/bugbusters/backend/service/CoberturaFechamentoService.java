package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.cobertura.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.CoberturaFechamentoIncompletaException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.importbase.ImportStatus;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.bugbusters.backend.service.pendencia.AceitePendenciaProvider;
import com.bugbusters.backend.service.pendencia.StatusAceitePendenciaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CoberturaFechamentoService {

    private static final Logger log = LoggerFactory.getLogger(CoberturaFechamentoService.class);

    private final CampanhaRepository campanhaRepository;
    private final CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;
    private final EnvioArquivoRepository envioArquivoRepository;
    private final InconsistenciaImportacaoRepository inconsistenciaRepository;
    private final RegraRepository regraRepository;
    private final SaleRepository saleRepository;
    private final RegistrationRepository registrationRepository;
    private final CoberturaBaseService coberturaBaseService;
    private final ReferenciaPublicoService referenciaPublicoService;
    private final TravaCompetencia travaCompetencia;
    private final FechamentoService fechamentoService;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final AceitePendenciaProvider aceitePendenciaProvider;

    public CoberturaFechamentoService(
            CampanhaRepository campanhaRepository,
            CampanhaAnexoBaseRepository campanhaAnexoBaseRepository,
            EnvioArquivoRepository envioArquivoRepository,
            InconsistenciaImportacaoRepository inconsistenciaRepository,
            RegraRepository regraRepository,
            SaleRepository saleRepository,
            RegistrationRepository registrationRepository,
            CoberturaBaseService coberturaBaseService,
            ReferenciaPublicoService referenciaPublicoService,
            TravaCompetencia travaCompetencia,
            FechamentoService fechamentoService,
            UsuarioAtualProvider usuarioAtualProvider,
            AceitePendenciaProvider aceitePendenciaProvider) {
        this.campanhaRepository = campanhaRepository;
        this.campanhaAnexoBaseRepository = campanhaAnexoBaseRepository;
        this.envioArquivoRepository = envioArquivoRepository;
        this.inconsistenciaRepository = inconsistenciaRepository;
        this.regraRepository = regraRepository;
        this.saleRepository = saleRepository;
        this.registrationRepository = registrationRepository;
        this.coberturaBaseService = coberturaBaseService;
        this.referenciaPublicoService = referenciaPublicoService;
        this.travaCompetencia = travaCompetencia;
        this.fechamentoService = fechamentoService;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.aceitePendenciaProvider = aceitePendenciaProvider;
    }

    /**
     * Decompõe o intervalo [dataInicio, dataFim] em todas as competências mensais (YYYY-MM) que intersectam o período.
     */
    public List<YearMonth> listarCompetenciasInterseccao(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            return Collections.emptyList();
        }
        if (inicio.isAfter(fim)) {
            throw new BusinessException("Data de início não pode ser posterior à data de fim.");
        }

        List<YearMonth> competencias = new ArrayList<>();
        YearMonth atual = YearMonth.from(inicio);
        YearMonth limite = YearMonth.from(fim);

        while (!atual.isAfter(limite)) {
            competencias.add(atual);
            atual = atual.plusMonths(1);
        }
        return competencias;
    }

    /**
     * Diagnóstico completo de cobertura da campanha para o fechamento (somente leitura, não adquire travas).
     * Avalia estritamente a versaoVigente da campanha.
     */
    @Transactional(readOnly = true)
    public CoberturaCampanhaResponseDTO diagnosticarCobertura(Long campanhaId) {
        Campanha campanha = buscarCampanha(campanhaId);

        CampanhaVersao versaoVigente = campanha.getVersaoVigente();
        if (versaoVigente == null) {
            return new CoberturaCampanhaResponseDTO(
                    campanha.getId(),
                    campanha.getTitulo(),
                    campanha.getEstado().name(),
                    null,
                    null,
                    null,
                    false,
                    0,
                    0,
                    0,
                    List.of("A campanha não possui versão vigente publicada."),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        LocalDate dataInicio = versaoVigente.getDataInicio();
        LocalDate dataFim = versaoVigente.getDataFim();
        List<YearMonth> competenciasVigencia = listarCompetenciasInterseccao(dataInicio, dataFim);

        List<CampanhaAnexoBase> anexosAtivos = campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanhaId);
        List<AnexoBaseCampanhaResponseDTO> anexosDTO = anexosAtivos.stream().map(this::toAnexoResponseDTO).toList();

        List<EnvioArquivo> lotesHrConcluidos = envioArquivoRepository.findLotesConcluidosValidos("HR", null);

        List<Regra> regras = regraRepository.findAllByCampanhaVersaoIdAndRemovidoEmIsNullOrderByIdAsc(versaoVigente.getId());
        if (regras.isEmpty()) {
            regras = regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId());
        }

        List<RegraItemRequest> regrasRequest = regras.stream()
                .map(referenciaPublicoService::toItemRequest)
                .toList();

        List<CoberturaCompetenciaDTO> competenciasResultado = new ArrayList<>();
        List<String> resumoImpedimentosGerais = new ArrayList<>();
        int competenciasCobertas = 0;

        for (YearMonth ym : competenciasVigencia) {
            LocalDate inicioRecorte = dataInicio.isAfter(ym.atDay(1)) ? dataInicio : ym.atDay(1);
            LocalDate fimRecorte = dataFim.isBefore(ym.atEndOfMonth()) ? dataFim : ym.atEndOfMonth();
            boolean mesParcial = !inicioRecorte.equals(ym.atDay(1)) || !fimRecorte.equals(ym.atEndOfMonth());

            List<String> bloqueiosCompetencia = new ArrayList<>();

            // 1. Lotes concluídos disponíveis no sistema para a competência
            List<EnvioArquivo> lotesSalesConcluidosMes = envioArquivoRepository.findLotesConcluidosValidos("SALES", ym.toString());
            List<EnvioArquivo> lotesComissConcluidosMes = envioArquivoRepository.findLotesConcluidosValidos("COMISSIONS", ym.toString());

            // 2. Anexos ativos da campanha para a competência
            List<CampanhaAnexoBase> anexosHr = anexosAtivos.stream()
                    .filter(a -> "HR".equalsIgnoreCase(a.getTipoBase()))
                    .toList();
            List<CampanhaAnexoBase> anexosSalesMes = anexosAtivos.stream()
                    .filter(a -> "SALES".equalsIgnoreCase(a.getTipoBase()) && ym.toString().equals(a.getCompetencia()))
                    .toList();
            List<CampanhaAnexoBase> anexosComissMes = anexosAtivos.stream()
                    .filter(a -> "COMISSIONS".equalsIgnoreCase(a.getTipoBase()) && ym.toString().equals(a.getCompetencia()))
                    .toList();

            Set<Long> reportIdsHrAnexados = anexosHr.stream().map(a -> a.getEnvioArquivo().getReportId()).collect(Collectors.toSet());
            Set<Long> reportIdsSalesAnexados = anexosSalesMes.stream().map(a -> a.getEnvioArquivo().getReportId()).collect(Collectors.toSet());
            Set<Long> reportIdsComissAnexados = anexosComissMes.stream().map(a -> a.getEnvioArquivo().getReportId()).collect(Collectors.toSet());

            List<LoteDisponivelParaAnexoDTO> lotesHrDisponiveisNaoAnexados = lotesHrConcluidos.stream()
                    .filter(l -> !reportIdsHrAnexados.contains(l.getReportId()))
                    .map(this::toLoteDisponivelDTO)
                    .toList();
            List<LoteDisponivelParaAnexoDTO> lotesSalesDisponiveisNaoAnexados = lotesSalesConcluidosMes.stream()
                    .filter(l -> !reportIdsSalesAnexados.contains(l.getReportId()))
                    .map(this::toLoteDisponivelDTO)
                    .toList();
            List<LoteDisponivelParaAnexoDTO> lotesComissDisponiveisNaoAnexados = lotesComissConcluidosMes.stream()
                    .filter(l -> !reportIdsComissAnexados.contains(l.getReportId()))
                    .map(this::toLoteDisponivelDTO)
                    .toList();

            // 3. Vendas no mês e no recorte
            List<Sale> vendasMes = saleRepository.findVendasPorPeriodoComRelacionamentos(ym.atDay(1), ym.atEndOfMonth());
            List<Sale> vendasRecorte = vendasMes.stream()
                    .filter(s -> !s.getSaleDate().isBefore(inicioRecorte) && !s.getSaleDate().isAfter(fimRecorte))
                    .toList();

            // Vendas elegíveis ao público-alvo da campanha
            List<Sale> vendasElegiveis = vendasRecorte.stream()
                    .filter(sale -> regrasRequest.stream().anyMatch(req -> referenciaPublicoService.atendePublico(req, sale)))
                    .toList();

            // 4. Inconsistências e pendências nos lotes anexados
            List<Long> envioIdsSalesMes = anexosSalesMes.stream().map(a -> a.getEnvioArquivo().getId()).toList();
            List<Long> envioIdsComissMes = anexosComissMes.stream().map(a -> a.getEnvioArquivo().getId()).toList();
            List<Long> envioIdsHr = anexosHr.stream().map(a -> a.getEnvioArquivo().getId()).toList();

            ResumoPendenciasCalculado pendenciasSales = calcularPendenciasLotes(campanhaId, envioIdsSalesMes);
            ResumoPendenciasCalculado pendenciasComiss = calcularPendenciasLotes(campanhaId, envioIdsComissMes);
            ResumoPendenciasCalculado pendenciasHr = calcularPendenciasLotes(campanhaId, envioIdsHr);

            // Avisos de sobreposição entre lotes SALES na competência
            List<AvisoSobreposicaoVendaDTO> avisosSobreposicao = identificarSobreposicoesVendas(vendasMes);

            // Diagnóstico de Vendas
            boolean vendasCoberto = true;
            String statusVendas = "COBERTO";
            String msgVendas = String.format("%d vendas elegíveis cobertas e lotes da competência anexados.", vendasElegiveis.size());

            if (anexosSalesMes.isEmpty()) {
                vendasCoberto = false;
                statusVendas = lotesSalesConcluidosMes.isEmpty() ? "SEM_BASE_NA_COMPETENCIA" : "FALTA_ANEXO";
                bloqueiosCompetencia.add("FALTA_ANEXO_VENDAS");
                msgVendas = lotesSalesConcluidosMes.isEmpty()
                        ? "Nenhum lote de vendas importado para a competência " + ym + "."
                        : String.format("Nenhum lote de vendas anexado para %s. Existem %d lote(s) disponível(is) para associar.", ym, lotesSalesDisponiveisNaoAnexados.size());
            } else if (!lotesSalesDisponiveisNaoAnexados.isEmpty()) {
                vendasCoberto = false;
                statusVendas = "FALTA_ANEXO";
                bloqueiosCompetencia.add("FALTA_ANEXO_VENDAS");
                msgVendas = String.format("Existem %d lote(s) concluído(s) na competência %s ainda não anexado(s) à campanha.",
                        lotesSalesDisponiveisNaoAnexados.size(), ym);
            } else if (pendenciasSales.totalNaoAceitas() > 0 || pendenciasSales.possuiImpeditivo()) {
                vendasCoberto = false;
                statusVendas = "COM_PENDENCIAS_NAO_ACEITAS";
                bloqueiosCompetencia.add("PENDENCIA_NAO_ACEITA_VENDAS");
                msgVendas = String.format("Existem %d pendência(s) sem aceite nos lotes de vendas de %s.", pendenciasSales.totalNaoAceitas(), ym);
            }

            // Diagnóstico de RH
            boolean rhCoberto = true;
            String statusRh = "COBERTO";
            String msgRh = "Cobertura de RH validada.";
            List<String> colaboradoresSemCargo = new ArrayList<>();
            List<AvisoVigenciaContratualDTO> avisosVigenciaContratual = new ArrayList<>();
            Set<Registration> colaboradoresComVendas = new HashSet<>();

            for (Sale s : vendasRecorte) {
                Registration reg = s.getRegistration();
                if (reg != null) {
                    colaboradoresComVendas.add(reg);
                    if (reg.getPosition() == null) {
                        String mat = reg.getRegistration() != null ? reg.getRegistration() : "ID:" + reg.getId();
                        if (!colaboradoresSemCargo.contains(mat)) {
                            colaboradoresSemCargo.add(mat);
                        }
                    }
                    if ((reg.getAdmissDate() != null && reg.getAdmissDate().isAfter(s.getSaleDate()))
                            || (reg.getDemissDate() != null && reg.getDemissDate().isBefore(s.getSaleDate()))) {
                        avisosVigenciaContratual.add(new AvisoVigenciaContratualDTO(
                                reg.getRegistration(),
                                null,
                                reg.getAdmissDate(),
                                reg.getDemissDate(),
                                s.getSaleDate()
                        ));
                    }
                }
            }

            long pendenciasMatriculaNaoAceitasEmVendas = pendenciasSales.contagemMatriculaNaoAceitas();

            if (anexosHr.isEmpty()) {
                rhCoberto = false;
                statusRh = lotesHrConcluidos.isEmpty() ? "SEM_BASE_HR" : "FALTA_ANEXO";
                bloqueiosCompetencia.add("FALTA_ANEXO_RH");
                msgRh = lotesHrConcluidos.isEmpty()
                        ? "Nenhum lote de RH cadastrado no sistema."
                        : "Nenhum lote de RH anexado à campanha.";
            } else if (!colaboradoresSemCargo.isEmpty()) {
                rhCoberto = false;
                statusRh = "COLABORADOR_SEM_CARGO";
                bloqueiosCompetencia.add("COLABORADOR_SEM_CARGO");
                msgRh = String.format("Existem %d colaborador(es) com vendas no recorte sem cargo cadastrado: %s.",
                        colaboradoresSemCargo.size(), String.join(", ", colaboradoresSemCargo));
            } else if (pendenciasMatriculaNaoAceitasEmVendas > 0) {
                rhCoberto = false;
                statusRh = "PENDENCIA_MATRICULA_EM_VENDAS";
                bloqueiosCompetencia.add("PENDENCIA_MATRICULA_EM_VENDAS");
                msgRh = String.format("Existem %d pendência(s) de matrícula não cadastrada no lote de vendas da competência %s.",
                        pendenciasMatriculaNaoAceitasEmVendas, ym);
            } else if (pendenciasHr.possuiImpeditivo()) {
                rhCoberto = false;
                statusRh = "COM_PENDENCIAS_IMPEDITIVAS";
                bloqueiosCompetencia.add("PENDENCIA_IMPEDITIVA_RH");
                msgRh = "Lote de RH anexado possui pendências impeditivas não resolvidas.";
            }

            // Diagnóstico de Taxas Base
            boolean taxasCoberto = true;
            String statusTaxas = "COBERTO";
            String msgTaxas = "Taxas base verificadas com sucesso.";
            List<ReferenciaFaltanteDTO> faltantes = Collections.emptyList();
            int totalParesExigidos = 0;

            if (anexosComissMes.isEmpty() && !lotesComissConcluidosMes.isEmpty()) {
                taxasCoberto = false;
                statusTaxas = "FALTA_ANEXO";
                bloqueiosCompetencia.add("FALTA_ANEXO_TAXAS");
                msgTaxas = String.format("Lote de taxas base da competência %s ainda não foi anexado à campanha.", ym);
            } else if (!lotesComissDisponiveisNaoAnexados.isEmpty()) {
                taxasCoberto = false;
                statusTaxas = "FALTA_ANEXO";
                bloqueiosCompetencia.add("FALTA_ANEXO_TAXAS");
                msgTaxas = String.format("Existem %d lote(s) de taxas concluído(s) em %s ainda não anexado(s).",
                        lotesComissDisponiveisNaoAnexados.size(), ym);
            } else if (vendasElegiveis.isEmpty()) {
                statusTaxas = "SEM_VENDAS_ELEGIVEIS";
                msgTaxas = "Nenhuma venda elegível para o público-alvo da campanha no recorte de " + ym + ".";
                taxasCoberto = true;
            } else {
                faltantes = coberturaBaseService.verificarCobertura(vendasElegiveis, ym);
                Set<String> paresExigidos = vendasElegiveis.stream()
                        .map(s -> (s.getBrand() != null ? s.getBrand().getCode() : "null") + ":"
                                + (s.getRegistration() != null && s.getRegistration().getPosition() != null ? s.getRegistration().getPosition().getCode() : "null"))
                        .collect(Collectors.toSet());
                totalParesExigidos = paresExigidos.size();

                if (!faltantes.isEmpty()) {
                    taxasCoberto = false;
                    statusTaxas = "TAXA_FALTANTE";
                    bloqueiosCompetencia.add("FALTA_TAXA_BASE");
                    msgTaxas = String.format("Ausência de cobertura de taxa base para %d par(es) marca/cargo em %s.", faltantes.size(), ym);
                } else if (pendenciasComiss.totalNaoAceitas() > 0 || pendenciasComiss.possuiImpeditivo()) {
                    taxasCoberto = false;
                    statusTaxas = "COM_PENDENCIAS_NAO_ACEITAS";
                    bloqueiosCompetencia.add("PENDENCIA_NAO_ACEITA_TAXAS");
                    msgTaxas = String.format("Existem %d pendência(s) sem aceite nos lotes de comissões de %s.", pendenciasComiss.totalNaoAceitas(), ym);
                }
            }

            long totalPendenciasNaoAceitasCompetencia = pendenciasSales.totalNaoAceitas()
                    + pendenciasComiss.totalNaoAceitas()
                    + pendenciasHr.totalNaoAceitas();

            boolean competenciaCoberta = rhCoberto && vendasCoberto && taxasCoberto && bloqueiosCompetencia.isEmpty();
            if (competenciaCoberta) {
                competenciasCobertas++;
            } else {
                for (String b : bloqueiosCompetencia) {
                    resumoImpedimentosGerais.add(String.format("Competência %s: %s", ym, traduzirBloqueio(b, ym, msgVendas, msgRh, msgTaxas)));
                }
            }

            CoberturaRhDiagnosticoDTO rhDTO = new CoberturaRhDiagnosticoDTO(
                    rhCoberto,
                    statusRh,
                    new ArrayList<>(reportIdsHrAnexados),
                    lotesHrDisponiveisNaoAnexados,
                    colaboradoresComVendas.size(),
                    colaboradoresSemCargo,
                    pendenciasMatriculaNaoAceitasEmVendas,
                    avisosVigenciaContratual,
                    pendenciasHr.resumoDTO(),
                    msgRh
            );

            CoberturaVendasDiagnosticoDTO vendasDTO = new CoberturaVendasDiagnosticoDTO(
                    vendasCoberto,
                    statusVendas,
                    new ArrayList<>(reportIdsSalesAnexados),
                    lotesSalesDisponiveisNaoAnexados,
                    vendasMes.size(),
                    vendasRecorte.size(),
                    vendasElegiveis.size(),
                    avisosSobreposicao,
                    pendenciasSales.resumoDTO(),
                    msgVendas
            );

            CoberturaTaxasDiagnosticoDTO taxasDTO = new CoberturaTaxasDiagnosticoDTO(
                    taxasCoberto,
                    statusTaxas,
                    new ArrayList<>(reportIdsComissAnexados),
                    lotesComissDisponiveisNaoAnexados,
                    totalParesExigidos,
                    faltantes,
                    pendenciasComiss.resumoDTO(),
                    msgTaxas
            );

            competenciasResultado.add(new CoberturaCompetenciaDTO(
                    ym.toString(),
                    inicioRecorte,
                    fimRecorte,
                    mesParcial,
                    competenciaCoberta,
                    bloqueiosCompetencia,
                    totalPendenciasNaoAceitasCompetencia,
                    rhDTO,
                    vendasDTO,
                    taxasDTO
            ));
        }

        int totalCompetencias = competenciasVigencia.size();
        int competenciasPendentes = totalCompetencias - competenciasCobertas;
        boolean prontaParaFechamento = totalCompetencias > 0 && competenciasPendentes == 0 && resumoImpedimentosGerais.isEmpty();

        return new CoberturaCampanhaResponseDTO(
                campanha.getId(),
                campanha.getTitulo(),
                campanha.getEstado().name(),
                versaoVigente.getNumero(),
                dataInicio,
                dataFim,
                prontaParaFechamento,
                totalCompetencias,
                competenciasCobertas,
                competenciasPendentes,
                resumoImpedimentosGerais,
                anexosDTO,
                competenciasResultado
        );
    }

    /**
     * Valida a cobertura integral de todas as competências da versão vigente da campanha antes do fechamento.
     * Exige transação ativa (Propagation.MANDATORY) aberta pelo orquestrador de fechamento (BACK-14)
     * para que as travas de competência (TravaCompetencia) adquiridas aqui permaneçam retidas até o
     * commit ou rollback do cálculo definitivo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CoberturaCampanhaResponseDTO validarParaFechamento(Long campanhaId) {
        Campanha campanha = buscarCampanha(campanhaId);

        CampanhaVersao versaoVigente = campanha.getVersaoVigente();
        if (versaoVigente == null) {
            throw new CoberturaFechamentoIncompletaException(
                    "Operação impedida: a campanha " + campanhaId + " não possui versão vigente publicada.",
                    null,
                    List.of(new com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO("versaoVigente", "Campanha sem versão vigente publicada."))
            );
        }

        List<YearMonth> competenciasVigencia = listarCompetenciasInterseccao(versaoVigente.getDataInicio(), versaoVigente.getDataFim());
        TreeSet<String> compsOrdenadas = competenciasVigencia.stream()
                .map(YearMonth::toString)
                .collect(Collectors.toCollection(TreeSet::new));

        // Adquire travas em ordem lexicográfica garantida
        for (String comp : compsOrdenadas) {
            travaCompetencia.adquirirTrava(comp);
        }

        CoberturaCampanhaResponseDTO diagnostico = diagnosticarCobertura(campanhaId);
        if (!diagnostico.prontaParaFechamento()) {
            throw CoberturaFechamentoIncompletaException.aPartirDe(diagnostico);
        }

        return diagnostico;
    }

    /**
     * Associa um lote geral existente (HR, SALES ou COMISSIONS) como anexo da campanha.
     */
    @Transactional
    public AnexoBaseCampanhaResponseDTO anexarBase(Long campanhaId, AnexarBaseCampanhaRequestDTO request) {
        if (request == null || request.reportId() == null) {
            throw new BusinessException("O reportId do lote é obrigatório.");
        }

        Campanha campanha = buscarCampanha(campanhaId);

        EnvioArquivo lote = envioArquivoRepository.findByReportIdForUpdate(request.reportId())
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado com reportId: " + request.reportId()));

        if (!ImportStatus.CONCLUIDO.name().equalsIgnoreCase(lote.getStatus())) {
            throw new BusinessException("Não é possível anexar o lote " + request.reportId() + " pois seu status é '" + lote.getStatus() + "'. Apenas lotes CONCLUIDO podem ser anexados.");
        }
        if (lote.getExcluidoEm() != null) {
            throw new BusinessException("Não é possível anexar o lote " + request.reportId() + " pois ele já foi excluído.");
        }
        if (lote.isRejeicaoIntegral()) {
            throw new BusinessException("Não é possível anexar o lote " + request.reportId() + " pois sofreu rejeição integral na importação.");
        }

        // Valida se a competência intersecta a vigência da versaoVigente
        if (campanha.getVersaoVigente() != null && lote.getCompetencia() != null) {
            YearMonth loteYm = YearMonth.parse(lote.getCompetencia());
            List<YearMonth> compVigencia = listarCompetenciasInterseccao(
                    campanha.getVersaoVigente().getDataInicio(),
                    campanha.getVersaoVigente().getDataFim()
            );
            if (!compVigencia.contains(loteYm)) {
                throw new BusinessException(String.format(
                        "O lote reportId %d pertence à competência %s, que não intersecta a vigência da versão vigente da campanha (%s a %s).",
                        request.reportId(),
                        lote.getCompetencia(),
                        campanha.getVersaoVigente().getDataInicio(),
                        campanha.getVersaoVigente().getDataFim()
                ));
            }
        }

        if (campanhaAnexoBaseRepository.existsByCampanhaIdAndEnvioArquivoIdAndDesanexadoEmIsNull(campanhaId, lote.getId())) {
            throw new BusinessException("O lote reportId " + request.reportId() + " já está anexado a esta campanha.");
        }

        if (lote.getCompetencia() != null) {
            travaCompetencia.adquirirTrava(lote.getCompetencia());
        }

        // TODO BACK-1: paliativo temporário até autenticação via SecurityContext
        String responsavel = usuarioAtualProvider.obterUsuarioAtual(request.responsavel());

        CampanhaAnexoBase anexo = new CampanhaAnexoBase(
                campanha,
                lote,
                lote.getTipoBase().toUpperCase(),
                lote.getCompetencia(),
                responsavel
        );

        campanhaAnexoBaseRepository.save(anexo);
        log.info("Lote reportId {} ({}) anexado com sucesso à campanha {}", request.reportId(), lote.getTipoBase(), campanhaId);

        return toAnexoResponseDTO(anexo);
    }

    /**
     * Desanexa logicamente um lote da campanha.
     */
    @Transactional
    public void desanexarBase(Long campanhaId, UUID anexoId, String responsavel) {
        CampanhaAnexoBase anexo = campanhaAnexoBaseRepository.findByIdAndCampanhaIdAndDesanexadoEmIsNull(anexoId, campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Anexo ativo não encontrado com ID: " + anexoId));

        if (anexo.getCompetencia() != null) {
            YearMonth ym = YearMonth.parse(anexo.getCompetencia());
            fechamentoService.validarPeriodosDisponiveis(ym.atDay(1), ym.atEndOfMonth());
            travaCompetencia.adquirirTrava(anexo.getCompetencia());
        }

        // TODO BACK-14: verificar se a campanha já possui fechamento definitivo concluído ou em andamento e bloquear a desanexação

        // TODO BACK-1: paliativo temporário até autenticação via SecurityContext
        String autor = usuarioAtualProvider.obterUsuarioAtual(responsavel);
        anexo.desanexar(autor, OffsetDateTime.now());
        campanhaAnexoBaseRepository.save(anexo);

        log.info("Anexo {} da campanha {} desanexado com sucesso por {}", anexoId, campanhaId, autor);
    }

    /**
     * Lista todos os anexos ativos de uma campanha.
     */
    @Transactional(readOnly = true)
    public List<AnexoBaseCampanhaResponseDTO> listarAnexosAtivos(Long campanhaId) {
        buscarCampanha(campanhaId);
        return campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanhaId)
                .stream()
                .map(this::toAnexoResponseDTO)
                .toList();
    }

    /**
     * Consulta paginada das pendências/inconsistências dos lotes anexados à campanha.
     */
    @Transactional(readOnly = true)
    public Page<PendenciaBaseCoberturaDTO> listarPendencias(Long campanhaId, String competencia, String tipoBase, Pageable pageable) {
        buscarCampanha(campanhaId);

        List<CampanhaAnexoBase> anexos = campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanhaId);

        List<Long> envioIds = anexos.stream()
                .filter(a -> competencia == null || competencia.isBlank() || competencia.equals(a.getCompetencia()))
                .filter(a -> tipoBase == null || tipoBase.isBlank() || tipoBase.equalsIgnoreCase(a.getTipoBase()))
                .map(a -> a.getEnvioArquivo().getId())
                .distinct()
                .toList();

        if (envioIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<InconsistenciaImportacao> pagInconsistencias = inconsistenciaRepository.findByEnvioIdIn(envioIds, pageable);

        List<PendenciaBaseCoberturaDTO> dtos = pagInconsistencias.getContent().stream()
                .map(inc -> {
                    StatusAceitePendenciaDTO aceite = aceitePendenciaProvider.consultarAceite(campanhaId, inc.getEnvio().getId(), inc);
                    boolean exigeAceite = !"IMPEDITIVO".equalsIgnoreCase(inc.getSeveridade());
                    return new PendenciaBaseCoberturaDTO(
                            inc.getId(),
                            inc.getEnvio().getReportId(),
                            inc.getEnvio().getIdLote(),
                            inc.getEnvio().getTipoBase(),
                            inc.getEnvio().getCompetencia(),
                            inc.getLinha(),
                            inc.getCampo(),
                            inc.getMotivo(),
                            inc.getSeveridade(),
                            exigeAceite,
                            aceite.aceita(),
                            aceite.aceitaPor(),
                            aceite.aceitaEm()
                    );
                })
                .toList();

        return new PageImpl<>(dtos, pageable, pagInconsistencias.getTotalElements());
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    private Campanha buscarCampanha(Long campanhaId) {
        return campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId)
                .or(() -> campanhaRepository.findById(campanhaId).filter(c -> c.getRemovidoEm() == null))
                .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada: " + campanhaId));
    }

    private record ResumoPendenciasCalculado(
            long total,
            long totalNaoAceitas,
            long contagemMatriculaNaoAceitas,
            boolean possuiImpeditivo,
            ResumoPendenciasCoberturaDTO resumoDTO
    ) {}

    private ResumoPendenciasCalculado calcularPendenciasLotes(Long campanhaId, List<Long> envioIds) {
        if (envioIds == null || envioIds.isEmpty()) {
            return new ResumoPendenciasCalculado(0, 0, 0, false, ResumoPendenciasCoberturaDTO.vazio());
        }

        List<InconsistenciaImportacao> inconsistencias = inconsistenciaRepository.findByEnvioIdIn(envioIds);
        long total = inconsistencias.size();
        long totalNaoAceitas = 0;
        long contagemMatriculaNaoAceitas = 0;
        boolean possuiImpeditivo = false;
        Map<String, Long> porSeveridade = new LinkedHashMap<>();
        Map<String, Long> porCampo = new LinkedHashMap<>();

        for (InconsistenciaImportacao inc : inconsistencias) {
            String sev = inc.getSeveridade() != null ? inc.getSeveridade().toUpperCase() : "DESCONHECIDO";
            String campo = inc.getCampo() != null ? inc.getCampo() : "geral";

            porSeveridade.put(sev, porSeveridade.getOrDefault(sev, 0L) + 1);
            porCampo.put(campo, porCampo.getOrDefault(campo, 0L) + 1);

            if ("IMPEDITIVO".equalsIgnoreCase(sev)) {
                possuiImpeditivo = true;
                totalNaoAceitas++;
            } else {
                StatusAceitePendenciaDTO aceite = aceitePendenciaProvider.consultarAceite(campanhaId, inc.getEnvio().getId(), inc);
                if (!aceite.aceita()) {
                    totalNaoAceitas++;
                    if ("matricula".equalsIgnoreCase(campo)) {
                        contagemMatriculaNaoAceitas++;
                    }
                }
            }
        }

        ResumoPendenciasCoberturaDTO resumoDTO = new ResumoPendenciasCoberturaDTO(
                total,
                totalNaoAceitas,
                porSeveridade,
                porCampo
        );

        return new ResumoPendenciasCalculado(total, totalNaoAceitas, contagemMatriculaNaoAceitas, possuiImpeditivo, resumoDTO);
    }

    private List<AvisoSobreposicaoVendaDTO> identificarSobreposicoesVendas(List<Sale> vendas) {
        if (vendas == null || vendas.size() <= 1) {
            return Collections.emptyList();
        }

        record ChaveVenda(String matricula, LocalDate data, BigDecimal valor) {}

        Map<ChaveVenda, List<Sale>> agrupadas = vendas.stream()
                .filter(s -> s.getRegistration() != null && s.getSaleDate() != null && s.getValue() != null)
                .collect(Collectors.groupingBy(s -> new ChaveVenda(
                        s.getRegistration().getRegistration(),
                        s.getSaleDate(),
                        s.getValue()
                )));

        Map<String, Long> cacheLoteParaReportId = new HashMap<>();
        List<AvisoSobreposicaoVendaDTO> avisos = new ArrayList<>();
        for (Map.Entry<ChaveVenda, List<Sale>> entry : agrupadas.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<Long> reportIds = entry.getValue().stream()
                        .map(Sale::getIdLoteOrigem)
                        .filter(Objects::nonNull)
                        .distinct()
                        .map(idLote -> cacheLoteParaReportId.computeIfAbsent(idLote,
                                id -> envioArquivoRepository.findByIdLote(id).map(EnvioArquivo::getReportId).orElse(null)))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

                avisos.add(new AvisoSobreposicaoVendaDTO(
                        entry.getKey().matricula(),
                        entry.getKey().data(),
                        entry.getKey().valor(),
                        entry.getValue().size(),
                        reportIds
                ));
            }
        }
        return avisos;
    }

    private String traduzirBloqueio(String codigo, YearMonth ym, String msgVendas, String msgRh, String msgTaxas) {
        return switch (codigo) {
            case "FALTA_ANEXO_VENDAS" -> msgVendas;
            case "PENDENCIA_NAO_ACEITA_VENDAS" -> msgVendas;
            case "FALTA_ANEXO_RH" -> msgRh;
            case "COLABORADOR_SEM_CARGO" -> msgRh;
            case "PENDENCIA_MATRICULA_EM_VENDAS" -> msgRh;
            case "PENDENCIA_IMPEDITIVA_RH" -> msgRh;
            case "FALTA_ANEXO_TAXAS" -> msgTaxas;
            case "FALTA_TAXA_BASE" -> msgTaxas;
            case "PENDENCIA_NAO_ACEITA_TAXAS" -> msgTaxas;
            default -> codigo;
        };
    }

    private AnexoBaseCampanhaResponseDTO toAnexoResponseDTO(CampanhaAnexoBase anexo) {
        EnvioArquivo lote = anexo.getEnvioArquivo();
        return new AnexoBaseCampanhaResponseDTO(
                anexo.getId(),
                anexo.getCampanha().getId(),
                lote.getReportId(),
                lote.getIdLote(),
                anexo.getTipoBase(),
                anexo.getCompetencia(),
                lote.getNomeArquivo(),
                lote.getStatus(),
                lote.getLinhasValidas(),
                lote.getTotalLinhas() - lote.getLinhasValidas(),
                anexo.getAnexadoEm(),
                anexo.getAnexadoPor()
        );
    }

    private LoteDisponivelParaAnexoDTO toLoteDisponivelDTO(EnvioArquivo lote) {
        return new LoteDisponivelParaAnexoDTO(
                lote.getReportId(),
                lote.getIdLote(),
                lote.getTipoBase(),
                lote.getCompetencia(),
                lote.getNomeArquivo(),
                lote.getLinhasValidas(),
                lote.getTotalLinhas() - lote.getLinhasValidas(),
                lote.getFinalizadoEm()
        );
    }
}
