package com.bugbusters.backend.service.preparacao;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import com.bugbusters.backend.dto.preparacao.*;
import com.bugbusters.backend.dto.preparacao.impacto.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.importbase.ImportInconsistencySeverity;
import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.importbase.reader.PreparacaoExcelReader;
import com.bugbusters.backend.importbase.reader.PreparacaoExcelReader.LinhaLidaPreparacao;
import com.bugbusters.backend.importbase.validation.ErroValidacaoLinhaDTO;
import com.bugbusters.backend.importbase.validation.HrRowValidator;
import com.bugbusters.backend.importbase.validation.SalesRowValidator;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionResolver;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.CoberturaFechamentoService;
import com.bugbusters.backend.service.FechamentoService;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PreparacaoBaseService {

    private static final Logger log = LoggerFactory.getLogger(PreparacaoBaseService.class);

    private final CampanhaRepository campanhaRepository;
    private final LotePreparacaoRepository lotePreparacaoRepository;
    private final LinhaPreparacaoRepository linhaPreparacaoRepository;
    private final HistoricoEdicaoPreparacaoRepository historicoEdicaoRepository;
    private final HistoricoVersaoRegistroRepository historicoVersaoRepository;
    private final ArquivoImportacaoRepository arquivoImportacaoRepository;
    private final EnvioArquivoRepository envioArquivoRepository;
    private final InconsistenciaImportacaoRepository inconsistenciaRepository;
    private final CampanhaAnexoBaseRepository campanhaAnexoBaseRepository;
    private final RegistrationRepository registrationRepository;
    private final SaleRepository saleRepository;
    private final StoreResolver storeResolver;
    private final PositionResolver positionResolver;
    private final BrandResolver brandResolver;
    private final PreparacaoExcelReader excelReader;
    private final HrRowValidator hrRowValidator;
    private final SalesRowValidator salesRowValidator;
    private final CoberturaFechamentoService coberturaFechamentoService;
    private final FechamentoService fechamentoService;
    private final TravaCompetencia travaCompetencia;
    private final UsuarioAtualProvider usuarioAtualProvider;
    private final PropagacaoImpactoCorrecaoService propagacaoImpactoCorrecaoService;
    private final com.bugbusters.backend.service.AlertaEstatisticoService alertaEstatisticoService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.bugbusters.backend.repository.ResultadoCalculoRepository resultadoCalculoRepository;

    private synchronized long obterProximoReportId() {
        if (jdbcTemplate != null) {
            try {
                Long nextVal = jdbcTemplate.queryForObject("SELECT nextval('seq_envio_arquivo_report_id')", Long.class);
                if (nextVal != null && nextVal > 0) {
                    return nextVal;
                }
            } catch (Exception ex) {
                try {
                    jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS seq_envio_arquivo_report_id START WITH 1000000 INCREMENT BY 1");
                    Long nextVal = jdbcTemplate.queryForObject("SELECT nextval('seq_envio_arquivo_report_id')", Long.class);
                    if (nextVal != null && nextVal > 0) {
                        return nextVal;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return Math.max(1000000L, envioArquivoRepository.findMaxReportId() + 1);
    }

    public PreparacaoBaseService(
            CampanhaRepository campanhaRepository,
            LotePreparacaoRepository lotePreparacaoRepository,
            LinhaPreparacaoRepository linhaPreparacaoRepository,
            HistoricoEdicaoPreparacaoRepository historicoEdicaoRepository,
            HistoricoVersaoRegistroRepository historicoVersaoRepository,
            ArquivoImportacaoRepository arquivoImportacaoRepository,
            EnvioArquivoRepository envioArquivoRepository,
            InconsistenciaImportacaoRepository inconsistenciaRepository,
            CampanhaAnexoBaseRepository campanhaAnexoBaseRepository,
            RegistrationRepository registrationRepository,
            SaleRepository saleRepository,
            StoreResolver storeResolver,
            PositionResolver positionResolver,
            BrandResolver brandResolver,
            PreparacaoExcelReader excelReader,
            HrRowValidator hrRowValidator,
            SalesRowValidator salesRowValidator,
            CoberturaFechamentoService coberturaFechamentoService,
            FechamentoService fechamentoService,
            TravaCompetencia travaCompetencia,
            UsuarioAtualProvider usuarioAtualProvider,
            PropagacaoImpactoCorrecaoService propagacaoImpactoCorrecaoService,
            com.bugbusters.backend.service.AlertaEstatisticoService alertaEstatisticoService) {
        this.campanhaRepository = campanhaRepository;
        this.lotePreparacaoRepository = lotePreparacaoRepository;
        this.linhaPreparacaoRepository = linhaPreparacaoRepository;
        this.historicoEdicaoRepository = historicoEdicaoRepository;
        this.historicoVersaoRepository = historicoVersaoRepository;
        this.arquivoImportacaoRepository = arquivoImportacaoRepository;
        this.envioArquivoRepository = envioArquivoRepository;
        this.inconsistenciaRepository = inconsistenciaRepository;
        this.campanhaAnexoBaseRepository = campanhaAnexoBaseRepository;
        this.registrationRepository = registrationRepository;
        this.saleRepository = saleRepository;
        this.storeResolver = storeResolver;
        this.positionResolver = positionResolver;
        this.brandResolver = brandResolver;
        this.excelReader = excelReader;
        this.hrRowValidator = hrRowValidator;
        this.salesRowValidator = salesRowValidator;
        this.coberturaFechamentoService = coberturaFechamentoService;
        this.fechamentoService = fechamentoService;
        this.travaCompetencia = travaCompetencia;
        this.usuarioAtualProvider = usuarioAtualProvider;
        this.propagacaoImpactoCorrecaoService = propagacaoImpactoCorrecaoService;
        this.alertaEstatisticoService = alertaEstatisticoService;
    }

    /**
     * Inicia uma área de preparação para um arquivo novo enviado via upload dentro da campanha.
     */
    @Transactional
    public LotePreparacaoResponseDTO iniciarPreparacaoUpload(
            Long campanhaId,
            MultipartFile file,
            String tipoBase,
            String competencia,
            String origemCriacao,
            Long reportIdSubstituido,
            String responsavel) {

        Campanha campanha = buscarCampanha(campanhaId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("Informe um arquivo Excel não vazio.");
        }

        String tipoNorm = validarTipoBase(tipoBase);
        String compNorm = validarCompetencia(tipoNorm, competencia);
        validarVigenciaCampanhaEPeriodoDisponivel(campanha, compNorm);

        String autor = usuarioAtualProvider.obterUsuarioAtual(responsavel);
        String hash = calcularHashSha256(file);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "arquivo.xlsx";

        ArquivoImportacao arquivo = arquivoImportacaoRepository.findByHashConteudo(hash)
                .orElseGet(() -> {
                    try {
                        return arquivoImportacaoRepository.save(new ArquivoImportacao(
                                hash, filename, file.getContentType(), file.getSize(), file.getBytes(), autor));
                    } catch (Exception ex) {
                        throw new BusinessException("Falha ao salvar arquivo original: " + ex.getMessage());
                    }
                });

        EnvioArquivo envioOrigem = null;
        if (reportIdSubstituido != null) {
            envioOrigem = envioArquivoRepository.findByReportId(reportIdSubstituido)
                    .orElseThrow(() -> new ResourceNotFoundException("Lote a ser substituído não encontrado com reportId: " + reportIdSubstituido));
        }

        LotePreparacao lote = new LotePreparacao(
                campanha,
                tipoNorm,
                compNorm,
                filename,
                hash,
                arquivo,
                autor
        );
        lote.setOrigemCriacao(origemCriacao != null && !origemCriacao.isBlank() ? origemCriacao.toUpperCase() : "MANUAL");
        lote.setEnvioArquivoOrigem(envioOrigem);

        lote = lotePreparacaoRepository.save(lote);

        try (InputStream is = file.getInputStream()) {
            processarELerLinhasParaPreparacao(lote, is);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception ex) {
            throw new BusinessException("Falha ao processar arquivo para preparação: " + ex.getMessage());
        }

        return toLoteDTO(lote);
    }

    /**
     * Inicia uma área de preparação a partir de um lote já importado anteriormente.
     */
    @Transactional
    public LotePreparacaoResponseDTO iniciarPreparacaoDeLoteExistente(
            Long campanhaId,
            Long reportIdOrigem,
            String responsavel) {

        Campanha campanha = buscarCampanha(campanhaId);

        EnvioArquivo envioOrigem = envioArquivoRepository.findByReportId(reportIdOrigem)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado com reportId: " + reportIdOrigem));

        String tipoNorm = validarTipoBase(envioOrigem.getTipoBase());
        String compNorm = validarCompetencia(tipoNorm, envioOrigem.getCompetencia());
        validarVigenciaCampanhaEPeriodoDisponivel(campanha, compNorm);

        ArquivoImportacao arquivo = null;
        if (envioOrigem.getArquivoImportacaoId() != null) {
            arquivo = arquivoImportacaoRepository.findById(envioOrigem.getArquivoImportacaoId()).orElse(null);
        }
        if (arquivo == null && envioOrigem.getHashConteudo() != null) {
            arquivo = arquivoImportacaoRepository.findByHashConteudo(envioOrigem.getHashConteudo()).orElse(null);
        }

        if (arquivo == null) {
            throw new BusinessException("O arquivo original deste lote não está disponível para preparação automática.");
        }

        String autor = usuarioAtualProvider.obterUsuarioAtual(responsavel);

        LotePreparacao lote = new LotePreparacao(
                campanha,
                tipoNorm,
                compNorm,
                envioOrigem.getNomeArquivo(),
                arquivo.getHashConteudo(),
                arquivo,
                autor
        );
        lote.setEnvioArquivoOrigem(envioOrigem);
        lote.setOrigemCriacao("CORRECAO_LOTE_ANTERIOR");

        lote = lotePreparacaoRepository.save(lote);

        try (InputStream is = new ByteArrayInputStream(arquivo.getConteudo())) {
            processarELerLinhasParaPreparacao(lote, is);
        } catch (Exception ex) {
            throw new BusinessException("Falha ao reprocessar arquivo do lote: " + ex.getMessage());
        }

        return toLoteDTO(lote);
    }

    /**
     * Lista os lotes de preparação vinculados a uma campanha com filtros opcionais.
     */
    @Transactional(readOnly = true)
    public List<LotePreparacaoResponseDTO> listarLotes(Long campanhaId, String tipoBase, String competencia, String status) {
        buscarCampanha(campanhaId);
        return lotePreparacaoRepository.findByCampanhaIdAndFiltros(campanhaId, tipoBase, competencia, status)
                .stream()
                .map(this::toLoteDTO)
                .toList();
    }

    /**
     * Detalhes de um lote de preparação.
     */
    @Transactional(readOnly = true)
    public LotePreparacaoResponseDTO consultarLote(Long campanhaId, UUID preparacaoId) {
        buscarCampanha(campanhaId);
        LotePreparacao lote = buscarLote(preparacaoId, campanhaId);
        return toLoteDTO(lote);
    }

    /**
     * Consulta paginada das linhas do lote em preparação com múltiplos filtros.
     */
    @Transactional(readOnly = true)
    public Page<LinhaPreparacaoResponseDTO> listarLinhas(
            Long campanhaId,
            UUID preparacaoId,
            FiltroLinhaPreparacaoDTO filtro,
            Pageable pageable) {

        buscarCampanha(campanhaId);
        buscarLote(preparacaoId, campanhaId);

        Specification<LinhaPreparacao> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("lotePreparacao").get("id"), preparacaoId));

            if (filtro != null) {
                if (filtro.valida() != null) {
                    if (Boolean.TRUE.equals(filtro.valida())) {
                        predicates.add(cb.equal(root.get("statusLinha"), "VALIDA"));
                    } else {
                        predicates.add(cb.equal(root.get("statusLinha"), "INVALIDA"));
                    }
                }
                if (filtro.severidade() != null && !filtro.severidade().isBlank()) {
                    predicates.add(cb.equal(cb.upper(root.get("severidadeMaxima")), filtro.severidade().trim().toUpperCase()));
                }
                if (filtro.campoErro() != null && !filtro.campoErro().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("camposComErro")), "%" + filtro.campoErro().trim().toLowerCase() + "%"));
                }
                if (filtro.matricula() != null && !filtro.matricula().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("matricula")), "%" + filtro.matricula().trim().toLowerCase() + "%"));
                }
                if (filtro.dataInicio() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("dataReferencia"), filtro.dataInicio()));
                }
                if (filtro.dataFim() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("dataReferencia"), filtro.dataFim()));
                }
                if (filtro.editada() != null) {
                    predicates.add(cb.equal(root.get("editada"), filtro.editada()));
                }
                if (filtro.ignorada() != null) {
                    predicates.add(cb.equal(root.get("ignorarNaEfetivacao"), filtro.ignorada()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return linhaPreparacaoRepository.findAll(spec, pageable).map(this::toLinhaDTO);
    }

    /**
     * Edição de uma única linha em preparação com controle de concorrência otimista (@Version).
     */
    @Transactional
    public LinhaPreparacaoResponseDTO editarLinha(
            Long campanhaId,
            UUID preparacaoId,
            int numeroLinha,
            EditarLinhaPreparacaoRequestDTO request) {

        buscarCampanha(campanhaId);
        LotePreparacao lote = buscarLote(preparacaoId, campanhaId);

        LinhaPreparacao linha = editarLinhaInterno(lote, numeroLinha, request, true);
        return toLinhaDTO(linha);
    }

    /**
     * Edição em lote de múltiplas linhas (usado por IA-9 ou correções em massa).
     */
    @Transactional
    public LotePreparacaoResponseDTO editarLinhasEmLote(
            Long campanhaId,
            UUID preparacaoId,
            EditarLinhasLoteRequestDTO request) {

        buscarCampanha(campanhaId);
        LotePreparacao lote = buscarLote(preparacaoId, campanhaId);

        if (!"EM_PREPARACAO".equals(lote.getStatus())) {
            throw new BusinessException("Não é possível editar linhas de um lote com status '" + lote.getStatus() + "'.");
        }

        if (request != null && request.linhas() != null) {
            for (EditarLinhasLoteRequestDTO.ItemEdicaoLinhaDTO item : request.linhas()) {
                if (item.edicao() != null) {
                    editarLinhaInterno(lote, item.numeroLinha(), item.edicao(), false);
                }
            }
            atualizarContadoresLote(lote);
        }

        return toLoteDTO(lote);
    }

    private LinhaPreparacao editarLinhaInterno(
            LotePreparacao lote,
            int numeroLinha,
            EditarLinhaPreparacaoRequestDTO request,
            boolean recalcularContadores) {

        if (!"EM_PREPARACAO".equals(lote.getStatus())) {
            throw new BusinessException("Não é possível editar linhas de um lote com status '" + lote.getStatus() + "'. Apenas lotes EM_PREPARACAO podem ser editados.");
        }

        String origem = "MANUAL";
        if (request.origem() != null && !request.origem().isBlank()) {
            origem = request.origem().trim().toUpperCase();
            if (!"MANUAL".equals(origem) && !"IA".equals(origem)) {
                throw new BusinessException("Origem de edição inválida: '" + request.origem() + "'. Valores permitidos: MANUAL, IA.");
            }
        }

        LinhaPreparacao linha = linhaPreparacaoRepository.findByLotePreparacaoIdAndNumeroLinha(lote.getId(), numeroLinha)
                .orElseThrow(() -> new ResourceNotFoundException("Linha " + numeroLinha + " não encontrada no lote de preparação " + lote.getId()));

        // Controle de concorrência otimista
        if (request.versao() == null || !request.versao().equals(linha.getVersao())) {
            throw new ResourceConflictException(
                    String.format("Conflito de edição na linha %d: a versão enviada (%s) é inválida ou difere da versão atual (%d). Recarregue a linha antes de salvar.",
                            numeroLinha, request.versao(), linha.getVersao()),
                    List.of(new CampoInvalidoDTO("versao", "Versão desatualizada ou não informada. Recarregue a linha antes de salvar."))
            );
        }

        String autor = usuarioAtualProvider.obterUsuarioAtual(request.responsavel());
        Map<String, Object> valoresAnteriores = extrairValoresEditaveis(linha);

        // Aplica alterações
        List<String> alterados = new ArrayList<>();
        if (request.dataReferencia() != null && !Objects.equals(request.dataReferencia(), linha.getDataReferencia())) {
            linha.setDataReferencia(request.dataReferencia());
            alterados.add("dataReferencia");
        }
        if (request.codMarca() != null && !Objects.equals(request.codMarca(), linha.getCodMarca())) {
            linha.setCodMarca(request.codMarca());
            alterados.add("codMarca");
        }
        if (request.descrMarca() != null && !Objects.equals(request.descrMarca(), linha.getDescrMarca())) {
            linha.setDescrMarca(request.descrMarca());
            alterados.add("descrMarca");
        }
        if (request.codLoja() != null && !Objects.equals(request.codLoja(), linha.getCodLoja())) {
            linha.setCodLoja(request.codLoja());
            alterados.add("codLoja");
        }
        if (request.descrLoja() != null && !Objects.equals(request.descrLoja(), linha.getDescrLoja())) {
            linha.setDescrLoja(request.descrLoja());
            alterados.add("descrLoja");
        }
        if (request.matricula() != null && !Objects.equals(request.matricula(), linha.getMatricula())) {
            linha.setMatricula(request.matricula().trim());
            alterados.add("matricula");
        }
        if (request.dataAdmissao() != null && !Objects.equals(request.dataAdmissao(), linha.getDataAdmissao())) {
            linha.setDataAdmissao(request.dataAdmissao());
            alterados.add("dataAdmissao");
        }
        if (request.dataDemissao() != null && !Objects.equals(request.dataDemissao(), linha.getDataDemissao())) {
            linha.setDataDemissao(request.dataDemissao());
            alterados.add("dataDemissao");
        }
        if (request.codCargo() != null && !Objects.equals(request.codCargo(), linha.getCodCargo())) {
            linha.setCodCargo(request.codCargo());
            alterados.add("codCargo");
        }
        if (request.descrCargo() != null && !Objects.equals(request.descrCargo(), linha.getDescrCargo())) {
            linha.setDescrCargo(request.descrCargo());
            alterados.add("descrCargo");
        }
        if (request.valorVenda() != null && !Objects.equals(request.valorVenda(), linha.getValorVenda())) {
            linha.setValorVenda(request.valorVenda());
            alterados.add("valorVenda");
        }
        if (request.ignorarNaEfetivacao() != null && !Objects.equals(request.ignorarNaEfetivacao(), linha.isIgnorarNaEfetivacao())) {
            linha.setIgnorarNaEfetivacao(request.ignorarNaEfetivacao());
            alterados.add("ignorarNaEfetivacao");
        }
        if (request.registroAlvoId() != null && !Objects.equals(request.registroAlvoId(), linha.getRegistroAlvoId())) {
            linha.setRegistroAlvoId(request.registroAlvoId());
            alterados.add("registroAlvoId");
        }

        String errosAntes = linha.getErrosValidacaoJson();
        long versaoAntiga = linha.getVersao();

        // Revalida a linha imediatamente
        revalidarUnicaLinha(lote, linha);

        linha.setEditada(true);
        linha.setOrigemUltimaEdicao(origem);
        linha.setAtualizadoEm(OffsetDateTime.now());
        linha.setAtualizadoPor(autor);

        linha = linhaPreparacaoRepository.saveAndFlush(linha);
        long versaoNova = (linha.getVersao() != null && linha.getVersao() > versaoAntiga)
                ? linha.getVersao()
                : versaoAntiga + 1;

        Map<String, Object> valoresNovos = extrairValoresEditaveis(linha);
        String histAnteriorJson = toJson(valoresAnteriores);
        String histNovosJson = toJson(valoresNovos);

        HistoricoEdicaoPreparacao historico = new HistoricoEdicaoPreparacao(
                linha,
                lote,
                numeroLinha,
                versaoAntiga,
                versaoNova,
                linha.getOrigemUltimaEdicao(),
                histAnteriorJson,
                histNovosJson,
                String.join(", ", alterados),
                errosAntes,
                linha.getErrosValidacaoJson(),
                request.motivo(),
                autor
        );
        historicoEdicaoRepository.save(historico);

        // Atualiza contadores do lote se solicitado
        if (recalcularContadores) {
            atualizarContadoresLote(lote);
        }

        return linha;
    }

    /**
     * Consulta o histórico de edições de uma linha específica na preparação.
     */
    @Transactional(readOnly = true)
    public List<HistoricoEdicaoLinhaResponseDTO> consultarHistoricoEdicaoLinha(Long campanhaId, UUID preparacaoId, int numeroLinha) {
        buscarCampanha(campanhaId);
        buscarLote(preparacaoId, campanhaId);

        LinhaPreparacao linha = linhaPreparacaoRepository.findByLotePreparacaoIdAndNumeroLinha(preparacaoId, numeroLinha)
                .orElseThrow(() -> new ResourceNotFoundException("Linha " + numeroLinha + " não encontrada."));

        return historicoEdicaoRepository.findByLinhaPreparacaoIdOrderByEditadoEmDesc(linha.getId())
                .stream()
                .map(this::toHistoricoEdicaoDTO)
                .toList();
    }

    /**
     * Revalida o lote inteiro contra o estado atual do banco (ex.: resolve matrículas criadas recentemente).
     */
    @Transactional
    public LotePreparacaoResponseDTO revalidarLote(Long campanhaId, UUID preparacaoId, String responsavel) {
        buscarCampanha(campanhaId);
        LotePreparacao lote = buscarLote(preparacaoId, campanhaId);

        if (!"EM_PREPARACAO".equals(lote.getStatus())) {
            throw new BusinessException("Apenas lotes EM_PREPARACAO podem ser revalidados.");
        }

        executarRevalidacaoCompleta(lote);
        lote.setRevalidadoEm(OffsetDateTime.now());
        lote = lotePreparacaoRepository.save(lote);

        return toLoteDTO(lote);
    }

    /**
     * Consulta a prévia de impactos de um lote em preparação (BACK-6).
     */
    @Transactional(readOnly = true)
    public PreviaImpactoCorrecaoDTO consultarPreviaImpacto(Long campanhaId, UUID preparacaoId) {
        buscarCampanha(campanhaId);
        LotePreparacao lote = buscarLote(preparacaoId, campanhaId);
        if (!"EM_PREPARACAO".equals(lote.getStatus())) {
            throw new BusinessException("Apenas lotes com status EM_PREPARACAO permitem consulta de prévia de impacto.");
        }
        List<LinhaPreparacao> linhasAtivas = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(preparacaoId)
                .stream()
                .filter(l -> !l.isIgnorarNaEfetivacao())
                .toList();
        return propagacaoImpactoCorrecaoService.calcularPreviaImpacto(lote, linhasAtivas);
    }

    /**
     * Efetivação transacional na base geral (tb_registration ou tb_sales) com versionamento e origem.
     */
    @Transactional
    public EfetivacaoPreparacaoResponseDTO efetivarLote(
            Long campanhaId,
            UUID preparacaoId,
            EfetivarLotePreparacaoRequestDTO request) {

        Campanha campanha = buscarCampanha(campanhaId);
        LotePreparacao lote = lotePreparacaoRepository.findByIdAndCampanhaIdForUpdate(preparacaoId, campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de preparação não encontrado: " + preparacaoId));

        if (!"EM_PREPARACAO".equals(lote.getStatus())) {
            throw new ResourceConflictException("Apenas lotes com status EM_PREPARACAO podem ser efetivados.");
        }

        // 1. Revalidação de garantia imediata antes de efetivar
        executarRevalidacaoCompleta(lote);

        List<LinhaPreparacao> linhasAtivas = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(preparacaoId)
                .stream()
                .filter(l -> !l.isIgnorarNaEfetivacao())
                .toList();

        if (linhasAtivas.isEmpty()) {
            throw new BusinessException("Não há linhas ativas para efetivação no lote " + preparacaoId);
        }

        // 2. Erros IMPEDITIVOS bloqueiam com 422
        List<CampoInvalidoDTO> impeditivos = new ArrayList<>();
        for (LinhaPreparacao l : linhasAtivas) {
            if ("IMPEDITIVO".equalsIgnoreCase(l.getSeveridadeMaxima())) {
                List<ErroValidacaoLinhaDTO> erros = fromJsonListaErros(l.getErrosValidacaoJson());
                for (ErroValidacaoLinhaDTO e : erros) {
                    if (e.severidade() == ImportInconsistencySeverity.IMPEDITIVO) {
                        impeditivos.add(new CampoInvalidoDTO("linha:" + l.getNumeroLinha() + "." + e.campo(), e.motivo()));
                    }
                }
            }
        }

        if (!impeditivos.isEmpty()) {
            throw new BusinessException("Efetivação impedida: o lote de preparação possui " + impeditivos.size() + " erro(s) IMPEDITIVO(S). Corrija ou ignore as linhas antes de efetivar.");
        }

        // 3. Prévia de impacto e controle de confirmação (BACK-6)
        PreviaImpactoCorrecaoDTO previa = propagacaoImpactoCorrecaoService.calcularPreviaImpacto(lote, linhasAtivas);
        if (previa.possuiBloqueioFechamento()) {
            throw new BusinessException("Efetivação bloqueada por competência fechada: " + String.join("; ", previa.bloqueiosFechamento()));
        }
        if (previa.exigeConfirmacaoExplicita() && (request == null || !Boolean.TRUE.equals(request.confirmarPropagacaoImpacto()))) {
            List<CampoInvalidoDTO> validacoes = new ArrayList<>();
            for (CampanhaAfetadaDTO c : previa.campanhasAfetadas()) {
                validacoes.add(new CampoInvalidoDTO("campanha:" + c.campanhaId(),
                        "Campanha '" + c.titulo() + "' afetada por regras dependentes."));
            }
            for (SimulacaoAfetadaDTO s : previa.simulacoesAfetadas()) {
                validacoes.add(new CampoInvalidoDTO("simulacao:" + s.simulacaoId(),
                        s.motivoImpacto()));
            }
            for (CalculoAfetadoDTO k : previa.calculosAfetados()) {
                validacoes.add(new CampoInvalidoDTO("calculo:" + k.competencia(),
                        k.motivoImpacto()));
            }
            for (AprovacaoCodigoAfetadaDTO a : previa.aprovacoesCodigoAfetadas()) {
                validacoes.add(new CampoInvalidoDTO("regra:" + a.regraId(),
                        a.motivoImpacto()));
            }
            throw new ResourceConflictException(
                    "A efetivação desta correção possui impactos colaterais em campanhas, simulações ou cálculos dependentes. Consulte a prévia de impacto e confirme explicitamente a propagação informando confirmarPropagacaoImpacto=true.",
                    validacoes
            );
        }

        // 4. Trava de competência e validação de período fechado
        if ("SALES".equalsIgnoreCase(lote.getTipoBase()) && lote.getCompetencia() != null) {
            YearMonth ym = YearMonth.parse(lote.getCompetencia());
            fechamentoService.validarPeriodosDisponiveis(ym.atDay(1), ym.atEndOfMonth());
            travaCompetencia.adquirirTrava(lote.getCompetencia());
        }

        String autor = usuarioAtualProvider.obterUsuarioAtual(request != null ? request.responsavel() : null);
        OffsetDateTime agora = OffsetDateTime.now();
        String idLoteGerado = UUID.randomUUID().toString();

        int registrosInseridos = 0;
        int registrosAtualizados = 0;
        int registrosIgnoradosDuplicados = 0;
        int advertenciasRegistradas = 0;
        List<InconsistenciaImportacao> inconsistenciasParaLote = new ArrayList<>();

        if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
            // EFETIVAÇÃO DE RH
            Set<String> matriculasLote = linhasAtivas.stream()
                    .map(LinhaPreparacao::getMatricula)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, Registration> existentes = registrationRepository.findByRegistrationIn(matriculasLote).stream()
                    .collect(Collectors.toMap(r -> r.getRegistration().toUpperCase(), r -> r));

            Set<String> processadasNesteLote = new HashSet<>();
            for (LinhaPreparacao l : linhasAtivas) {
                String mat = l.getMatricula() != null ? l.getMatricula().trim() : null;
                if (mat == null || !processadasNesteLote.add(mat.toUpperCase())) {
                    continue; // Deduplica matrícula dentro do lote de RH
                }

                Store store = storeResolver.resolveOrCreate(l.getCodLoja(), l.getDescrLoja());
                Position position = positionResolver.resolveOrCreate(l.getCodCargo(), l.getDescrCargo());

                Registration reg = existentes.get(mat.toUpperCase());
                if (reg == null) {
                    // Novo cadastro
                    reg = new Registration();
                    reg.setRegistration(mat);
                    reg.setStore(store);
                    reg.setPosition(position);
                    reg.setAdmissDate(l.getDataAdmissao());
                    reg.setDemissDate(l.getDataDemissao());
                    reg.setIdLoteOrigem(idLoteGerado);
                    reg.setVersao(1);
                    reg.setOrigemUltimaAlteracao("PREPARACAO_CAMPANHA");
                    reg.setIdLoteUltimaAlteracao(idLoteGerado);
                    reg.setAtualizadoEm(agora);
                    reg.setAtualizadoPor(autor);

                    reg = registrationRepository.save(reg);
                    registrosInseridos++;

                    HistoricoVersaoRegistro hist = new HistoricoVersaoRegistro(
                            "HR", reg.getId(), mat, 1, "CRIACAO", l.getOrigemUltimaEdicao(),
                            campanha, lote, idLoteGerado, null, toJson(extrairValoresRegistroRh(reg)), autor
                    );
                    historicoVersaoRepository.save(hist);
                } else {
                    // Atualização de matrícula existente
                    Map<String, Object> valoresAntes = extrairValoresRegistroRh(reg);
                    boolean mudou = !Objects.equals(reg.getStore().getId(), store.getId())
                            || !Objects.equals(reg.getPosition().getId(), position.getId())
                            || !Objects.equals(reg.getAdmissDate(), l.getDataAdmissao())
                            || !Objects.equals(reg.getDemissDate(), l.getDataDemissao());

                    if (mudou) {
                        reg.setStore(store);
                        reg.setPosition(position);
                        reg.setAdmissDate(l.getDataAdmissao());
                        reg.setDemissDate(l.getDataDemissao());
                        reg.setVersao(reg.getVersao() + 1);
                        reg.setOrigemUltimaAlteracao(l.getOrigemUltimaEdicao());
                        reg.setIdLoteUltimaAlteracao(idLoteGerado);
                        reg.setAtualizadoEm(agora);
                        reg.setAtualizadoPor(autor);

                        reg = registrationRepository.save(reg);
                        registrosAtualizados++;

                        HistoricoVersaoRegistro hist = new HistoricoVersaoRegistro(
                                "HR", reg.getId(), mat, reg.getVersao(), "ATUALIZACAO", l.getOrigemUltimaEdicao(),
                                campanha, lote, idLoteGerado, toJson(valoresAntes), toJson(extrairValoresRegistroRh(reg)), autor
                        );
                        historicoVersaoRepository.save(hist);
                    }
                }
            }

        } else if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
            // EFETIVAÇÃO DE VENDAS
            Set<String> matriculasVendas = linhasAtivas.stream()
                    .map(LinhaPreparacao::getMatricula)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, Registration> colaboradores = registrationRepository.findByRegistrationIn(matriculasVendas).stream()
                    .collect(Collectors.toMap(r -> r.getRegistration().toUpperCase(), r -> r));

            Set<String> chavesDeduplicadasLote = new HashSet<>();

            for (LinhaPreparacao l : linhasAtivas) {
                if (l.getMatricula() == null || l.getDataReferencia() == null || l.getValorVenda() == null) {
                    continue;
                }

                String chave = String.format("%s:%s:%d:%d:%s",
                        l.getMatricula().trim().toUpperCase(),
                        l.getDataReferencia(),
                        l.getCodLoja() != null ? l.getCodLoja() : 0,
                        l.getCodMarca() != null ? l.getCodMarca() : 0,
                        l.getValorVenda()
                );

                if (!chavesDeduplicadasLote.add(chave)) {
                    registrosIgnoradosDuplicados++;
                    continue; // Deduplica venda idêntica dentro do mesmo lote de preparação
                }

                Registration reg = colaboradores.get(l.getMatricula().trim().toUpperCase());

                if (reg == null) {
                    // Venda com matrícula não cadastrada (ADVERTENCIA)
                    advertenciasRegistradas++;
                    inconsistenciasParaLote.add(new InconsistenciaImportacao(
                            null,
                            l.getNumeroLinha(),
                            "matricula",
                            "Matrícula não cadastrada: " + l.getMatricula().trim(),
                            ImportInconsistencySeverity.ADVERTENCIA.name()
                    ));
                    continue; // Não grava venda na base geral sem matrícula
                }

                Store store = storeResolver.resolveOrCreate(l.getCodLoja(), l.getDescrLoja());
                Brand brand = brandResolver.resolveOrCreate(l.getCodMarca(), l.getDescrMarca());

                Sale saleAlvo = null;
                if (l.getRegistroAlvoId() != null) {
                    saleAlvo = saleRepository.findById(l.getRegistroAlvoId()).orElse(null);
                }
                if (saleAlvo == null) {
                    saleAlvo = saleRepository.findFirstByRegistrationIdAndSaleDateAndStoreIdAndBrandIdAndValueOrderByIdAsc(
                            reg.getId(), l.getDataReferencia(), store.getId(), brand.getId(), l.getValorVenda()
                    ).orElse(null);
                }

                if (saleAlvo != null) {
                    // Venda já existe na base geral com chave idêntica
                    if (l.getRegistroAlvoId() != null) {
                        // Atualização direcionada
                        Map<String, Object> valoresAntes = extrairValoresRegistroVenda(saleAlvo);

                        saleAlvo.setRegistration(reg);
                        saleAlvo.setStore(store);
                        saleAlvo.setBrand(brand);
                        saleAlvo.setValue(l.getValorVenda());
                        saleAlvo.setSaleDate(l.getDataReferencia());
                        saleAlvo.setVersao(saleAlvo.getVersao() + 1);
                        saleAlvo.setOrigemUltimaAlteracao(l.getOrigemUltimaEdicao());
                        saleAlvo.setIdLoteUltimaAlteracao(idLoteGerado);
                        saleAlvo.setAtualizadoEm(agora);
                        saleAlvo.setAtualizadoPor(autor);

                        saleAlvo = saleRepository.save(saleAlvo);
                        registrosAtualizados++;

                        HistoricoVersaoRegistro hist = new HistoricoVersaoRegistro(
                                "SALES", saleAlvo.getId(), chave, saleAlvo.getVersao(), "ATUALIZACAO",
                                l.getOrigemUltimaEdicao(), campanha, lote, idLoteGerado, toJson(valoresAntes),
                                toJson(extrairValoresRegistroVenda(saleAlvo)), autor
                        );
                        historicoVersaoRepository.save(hist);
                    } else {
                        registrosIgnoradosDuplicados++;
                    }
                } else {
                    // Nova venda inserida
                    Sale novaVenda = new Sale();
                    novaVenda.setRegistration(reg);
                    novaVenda.setStore(store);
                    novaVenda.setBrand(brand);
                    novaVenda.setValue(l.getValorVenda());
                    novaVenda.setSaleDate(l.getDataReferencia());
                    novaVenda.setTipoVenda("IMPORTADA");
                    novaVenda.setIdLoteOrigem(idLoteGerado);
                    novaVenda.setVersao(1);
                    novaVenda.setOrigemUltimaAlteracao(l.getOrigemUltimaEdicao());
                    novaVenda.setIdLoteUltimaAlteracao(idLoteGerado);
                    novaVenda.setAtualizadoEm(agora);
                    novaVenda.setAtualizadoPor(autor);

                    novaVenda = saleRepository.save(novaVenda);
                    registrosInseridos++;

                    HistoricoVersaoRegistro hist = new HistoricoVersaoRegistro(
                            "SALES", novaVenda.getId(), chave, 1, "CRIACAO", l.getOrigemUltimaEdicao(),
                            campanha, lote, idLoteGerado, null, toJson(extrairValoresRegistroVenda(novaVenda)), autor
                    );
                    historicoVersaoRepository.save(hist);
                }
            }
        }

        // 4. Criação do EnvioArquivo (Lote oficial)
        long proximoReportId = obterProximoReportId();

        EnvioArquivo novoEnvio = new EnvioArquivo();
        novoEnvio.setIdLote(idLoteGerado);
        novoEnvio.setReportId(proximoReportId);
        novoEnvio.setNomeArquivo(lote.getNomeArquivo());
        novoEnvio.setTipoBase(lote.getTipoBase());
        novoEnvio.setCompetencia(lote.getCompetencia());
        novoEnvio.setHashConteudo(lote.getHashConteudo());
        novoEnvio.setArquivoImportacaoId(lote.getArquivoImportacao().getId());
        novoEnvio.setOrigemLote("PREPARACAO_CAMPANHA");
        novoEnvio.setStatus("CONCLUIDO");
        novoEnvio.setTotalLinhas(linhasAtivas.size());
        novoEnvio.setLinhasValidas(registrosInseridos + registrosAtualizados);
        novoEnvio.setRejeicaoIntegral(false);
        novoEnvio.setCriadoEm(lote.getCriadoEm());
        novoEnvio.setIniciadoEm(agora);
        novoEnvio.setFinalizadoEm(agora);
        novoEnvio.setMensagem(String.format("Lote efetivado a partir da área de preparação da campanha %d por %s.", campanhaId, autor));

        if (lote.getEnvioArquivoOrigem() != null) {
            novoEnvio.setEnvioAnteriorId(lote.getEnvioArquivoOrigem().getId());
        }

        novoEnvio = envioArquivoRepository.saveAndFlush(novoEnvio);

        if (lote.getEnvioArquivoOrigem() != null) {
            lote.getEnvioArquivoOrigem().setSubstituidoPorId(novoEnvio.getId());
            envioArquivoRepository.save(lote.getEnvioArquivoOrigem());
        }

        // Salva inconsistências remanescentes (ADVERTENCIA de vendas)
        if (!inconsistenciasParaLote.isEmpty()) {
            for (InconsistenciaImportacao inc : inconsistenciasParaLote) {
                inc.setEnvio(novoEnvio);
            }
            inconsistenciaRepository.saveAll(inconsistenciasParaLote);
        }

        // 5. Integração com BACK-13: Anexação automática à campanha (substituindo lote anterior se aplicável)
        if (lote.getEnvioArquivoOrigem() != null) {
            campanhaAnexoBaseRepository.findByCampanhaIdAndDesanexadoEmIsNullOrderByAnexadoEmDesc(campanhaId)
                    .stream()
                    .filter(a -> a.getEnvioArquivo().getId().equals(lote.getEnvioArquivoOrigem().getId()))
                    .forEach(a -> {
                        a.desanexar(autor, agora);
                        campanhaAnexoBaseRepository.save(a);
                    });
        }

        CampanhaAnexoBase anexo = new CampanhaAnexoBase(
                campanha,
                novoEnvio,
                novoEnvio.getTipoBase().toUpperCase(),
                novoEnvio.getCompetencia(),
                autor
        );
        anexo = campanhaAnexoBaseRepository.save(anexo);

        // 6. Atualização do lote de preparação para EFETIVADO
        lote.setStatus("EFETIVADO");
        lote.setEnvioArquivoEfetivado(novoEnvio);
        lote.setEfetivadoEm(agora);
        lote.setEfetivadoPor(autor);
        lotePreparacaoRepository.save(lote);
        alertaEstatisticoService.analisarLoteEfetivado(lote);

        // 7. Propagação dos impactos confirmados para campanhas, simulações e cálculos
        ResumoPropagacaoImpactoDTO resumoImpactos = null;
        if (previa.possuiImpactos()) {
            resumoImpactos = propagacaoImpactoCorrecaoService.propagarImpactos(
                    lote, previa, autor, request != null ? request.motivo() : null
            );
        }

        log.info("Lote de preparação {} da campanha {} efetivado com sucesso: {} novos registros, {} atualizados, reportId {}",
                preparacaoId, campanhaId, registrosInseridos, registrosAtualizados, proximoReportId);

        return new EfetivacaoPreparacaoResponseDTO(
                preparacaoId,
                campanhaId,
                "EFETIVADO",
                proximoReportId,
                idLoteGerado,
                anexo.getId(),
                registrosInseridos,
                registrosAtualizados,
                registrosIgnoradosDuplicados,
                advertenciasRegistradas,
                agora,
                autor,
                resumoImpactos
        );
    }

    /**
     * Descarta logicamente um lote de preparação.
     */
    @Transactional
    public LotePreparacaoResponseDTO descartarLote(Long campanhaId, UUID preparacaoId, String motivo, String responsavel) {
        buscarCampanha(campanhaId);
        LotePreparacao lote = lotePreparacaoRepository.findByIdAndCampanhaIdForUpdate(preparacaoId, campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de preparação não encontrado: " + preparacaoId));

        if (!"EM_PREPARACAO".equals(lote.getStatus())) {
            throw new ResourceConflictException("Apenas lotes com status EM_PREPARACAO podem ser descartados.");
        }

        String autor = usuarioAtualProvider.obterUsuarioAtual(responsavel);
        lote.setStatus("DESCARTADO");
        lote.setDescartadoEm(OffsetDateTime.now());
        lote.setDescartadoPor(autor);
        lote.setMotivoDescarte(motivo);

        lote = lotePreparacaoRepository.save(lote);
        return toLoteDTO(lote);
    }

    /**
     * Download do arquivo original preservado.
     */
    @Transactional(readOnly = true)
    public ArquivoOriginalDownloadDTO baixarArquivoOriginal(Long campanhaId, UUID preparacaoId) {
        buscarCampanha(campanhaId);
        LotePreparacao lote = buscarLote(preparacaoId, campanhaId);
        ArquivoImportacao arq = lote.getArquivoImportacao();
        if (arq == null) {
            throw new ResourceNotFoundException("Arquivo original não encontrado para o lote " + preparacaoId);
        }
        return new ArquivoOriginalDownloadDTO(
                arq.getNomeArquivo(),
                arq.getContentType(),
                arq.getTamanhoBytes(),
                arq.getConteudo()
        );
    }

    /**
     * Consulta o histórico de versões de um registro na base geral vinculado a uma campanha.
     */
    @Transactional(readOnly = true)
    public List<HistoricoVersaoRegistroResponseDTO> consultarHistoricoRegistro(Long campanhaId, String tipoBase, String chaveOuId) {
        if (campanhaId != null) {
            buscarCampanha(campanhaId);
        }
        return consultarHistoricoRegistro(tipoBase, chaveOuId);
    }

    /**
     * Consulta o histórico de versões de um registro na base geral.
     */
    @Transactional(readOnly = true)
    public List<HistoricoVersaoRegistroResponseDTO> consultarHistoricoRegistro(String tipoBase, String chaveOuId) {
        if (chaveOuId == null || chaveOuId.isBlank()) {
            return Collections.emptyList();
        }

        try {
            UUID regId = UUID.fromString(chaveOuId);
            List<HistoricoVersaoRegistro> porId = historicoVersaoRepository.findByTipoBaseAndRegistroIdOrderByVersaoDesc(
                    tipoBase.toUpperCase(), regId);
            if (!porId.isEmpty()) {
                return porId.stream().map(this::toHistoricoVersaoDTO).toList();
            }
        } catch (IllegalArgumentException ignored) {}

        return historicoVersaoRepository.findByTipoBaseAndChaveNegocioOrderByVersaoDesc(tipoBase.toUpperCase(), chaveOuId.trim())
                .stream()
                .map(this::toHistoricoVersaoDTO)
                .toList();
    }

    // =========================================================================
    // MÉTODOS INTERNOS AUXILIARES
    // =========================================================================

    private void processarELerLinhasParaPreparacao(LotePreparacao lote, InputStream input) {
        List<LinhaLidaPreparacao> linhasLidas = excelReader.lerLinhas(input, lote.getTipoBase());

        Map<String, Long> contagemMatriculas = linhasLidas.stream()
                .map(LinhaLidaPreparacao::matricula)
                .filter(m -> m != null && !m.isBlank())
                .map(m -> m.trim().toUpperCase())
                .collect(Collectors.groupingBy(java.util.function.Function.identity(), Collectors.counting()));

        Set<String> matriculasDuplicadasNoArquivo = contagemMatriculas.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<String> matriculasNoArquivo = contagemMatriculas.keySet();
        Map<String, Registration> colaboradoresBanco = Collections.emptyMap();
        if (!matriculasNoArquivo.isEmpty()) {
            colaboradoresBanco = registrationRepository.findByRegistrationIn(matriculasNoArquivo).stream()
                    .collect(Collectors.toMap(r -> r.getRegistration().toUpperCase(), r -> r));
        }

        List<Sale> vendasOrigemDisponiveis = new ArrayList<>();
        if (lote.getEnvioArquivoOrigem() != null && "SALES".equalsIgnoreCase(lote.getTipoBase())) {
            String idLoteOrigem = lote.getEnvioArquivoOrigem().getIdLote();
            if (idLoteOrigem != null) {
                vendasOrigemDisponiveis.addAll(saleRepository.findByIdLoteOrigem(idLoteOrigem));
            }
        }

        List<Registration> rhOrigemDisponiveis = new ArrayList<>();
        if (lote.getEnvioArquivoOrigem() != null && "HR".equalsIgnoreCase(lote.getTipoBase())) {
            String idLoteOrigem = lote.getEnvioArquivoOrigem().getIdLote();
            if (idLoteOrigem != null) {
                rhOrigemDisponiveis.addAll(registrationRepository.findByIdLoteOrigem(idLoteOrigem));
            }
        }

        Set<String> chavesVendas = new HashSet<>();
        YearMonth compYm = lote.getCompetencia() != null ? YearMonth.parse(lote.getCompetencia()) : null;

        List<LinhaPreparacao> entidades = new ArrayList<>();
        int validas = 0;
        int invalidas = 0;
        int impeditivas = 0;
        int advertencias = 0;
        int avisos = 0;

        for (LinhaLidaPreparacao l : linhasLidas) {
            List<ErroValidacaoLinhaDTO> erros = new ArrayList<>(l.errosDeFormato());

            if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
                HrFileRow hrRow = new HrFileRow(
                        l.dataReferencia(), l.codMarca(), l.descrMarca(),
                        l.codLoja(), l.descrLoja(), l.matricula(),
                        l.dataAdmissao(), l.dataDemissao(),
                        l.codCargo(), l.descrCargo(), l.numeroLinha()
                );
                erros.addAll(hrRowValidator.validar(hrRow, matriculasDuplicadasNoArquivo, colaboradoresBanco));
            } else if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
                SalesFileRow salesRow = new SalesFileRow(
                        l.dataReferencia(), l.codMarca(), l.descrMarca(),
                        l.codLoja(), l.descrLoja(), l.matricula(),
                        l.valorVenda(), l.numeroLinha()
                );
                erros.addAll(salesRowValidator.validar(salesRow, compYm, colaboradoresBanco, chavesVendas));
                if (l.matricula() != null && l.dataReferencia() != null && l.codLoja() != null && l.codMarca() != null && l.valorVenda() != null) {
                    chavesVendas.add(String.format("%s:%s:%d:%d:%s", l.matricula().trim(), l.dataReferencia(), l.codLoja(), l.codMarca(), l.valorVenda()));
                }
            }

            String severidadeMax = calcularSeveridadeMaxima(erros);
            boolean isValid = erros.stream().noneMatch(e -> e.severidade() == ImportInconsistencySeverity.IMPEDITIVO
                    || e.severidade() == ImportInconsistencySeverity.ADVERTENCIA);

            if (isValid) {
                validas++;
            } else {
                invalidas++;
            }

            if ("IMPEDITIVO".equals(severidadeMax)) impeditivas++;
            if ("ADVERTENCIA".equals(severidadeMax)) advertencias++;
            if ("AVISO".equals(severidadeMax)) avisos++;

            UUID registroAlvoId = null;
            if (!vendasOrigemDisponiveis.isEmpty() && l.matricula() != null) {
                String matUpper = l.matricula().trim().toUpperCase();
                Iterator<Sale> it = vendasOrigemDisponiveis.iterator();
                while (it.hasNext()) {
                    Sale s = it.next();
                    boolean match = (s.getRegistration() != null && s.getRegistration().getRegistration().equalsIgnoreCase(matUpper))
                            && Objects.equals(s.getSaleDate(), l.dataReferencia())
                            && (s.getStore() != null && Objects.equals(s.getStore().getCode(), l.codLoja()))
                            && (s.getBrand() != null && Objects.equals(s.getBrand().getCode(), l.codMarca()))
                            && (s.getValue() != null && l.valorVenda() != null && s.getValue().compareTo(l.valorVenda()) == 0);
                    if (match) {
                        registroAlvoId = s.getId();
                        it.remove();
                        break;
                    }
                }
                if (registroAlvoId == null) {
                    it = vendasOrigemDisponiveis.iterator();
                    while (it.hasNext()) {
                        Sale s = it.next();
                        boolean match = (s.getRegistration() != null && s.getRegistration().getRegistration().equalsIgnoreCase(matUpper))
                                && Objects.equals(s.getSaleDate(), l.dataReferencia())
                                && (s.getStore() != null && Objects.equals(s.getStore().getCode(), l.codLoja()))
                                && (s.getBrand() != null && Objects.equals(s.getBrand().getCode(), l.codMarca()));
                        if (match) {
                            registroAlvoId = s.getId();
                            it.remove();
                            break;
                        }
                    }
                }
            } else if (!rhOrigemDisponiveis.isEmpty() && l.matricula() != null) {
                String matUpper = l.matricula().trim().toUpperCase();
                Iterator<Registration> it = rhOrigemDisponiveis.iterator();
                while (it.hasNext()) {
                    Registration r = it.next();
                    if (r.getRegistration() != null && r.getRegistration().equalsIgnoreCase(matUpper)) {
                        registroAlvoId = r.getId();
                        it.remove();
                        break;
                    }
                }
            }

            LinhaPreparacao linhaEntidade = new LinhaPreparacao(lote, l.numeroLinha(), l.conteudoOriginalJson());
            linhaEntidade.setRegistroAlvoId(registroAlvoId);
            linhaEntidade.setDataReferencia(l.dataReferencia());
            linhaEntidade.setCodMarca(l.codMarca());
            linhaEntidade.setDescrMarca(l.descrMarca());
            linhaEntidade.setCodLoja(l.codLoja());
            linhaEntidade.setDescrLoja(l.descrLoja());
            linhaEntidade.setMatricula(l.matricula());
            linhaEntidade.setDataAdmissao(l.dataAdmissao());
            linhaEntidade.setDataDemissao(l.dataDemissao());
            linhaEntidade.setCodCargo(l.codCargo());
            linhaEntidade.setDescrCargo(l.descrCargo());
            linhaEntidade.setValorVenda(l.valorVenda());
            linhaEntidade.setStatusLinha(isValid ? "VALIDA" : "INVALIDA");
            linhaEntidade.setSeveridadeMaxima(severidadeMax);
            linhaEntidade.setErrosValidacaoJson(toJson(erros));
            linhaEntidade.setCamposComErro(erros.stream().map(ErroValidacaoLinhaDTO::campo).distinct().collect(Collectors.joining(",")));

            entidades.add(linhaEntidade);
        }

        linhaPreparacaoRepository.saveAll(entidades);

        lote.setTotalLinhas(entidades.size());
        lote.setLinhasValidas(validas);
        lote.setLinhasInvalidas(invalidas);
        lote.setLinhasImpeditivas(impeditivas);
        lote.setLinhasAdvertencia(advertencias);
        lote.setLinhasAviso(avisos);
        lotePreparacaoRepository.save(lote);
    }

    private void revalidarUnicaLinha(LotePreparacao lote, LinhaPreparacao linha) {
        List<ErroValidacaoLinhaDTO> erros = new ArrayList<>();
        YearMonth compYm = lote.getCompetencia() != null ? YearMonth.parse(lote.getCompetencia()) : null;

        if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
            HrFileRow hrRow = new HrFileRow(
                    linha.getDataReferencia(), linha.getCodMarca(), linha.getDescrMarca(),
                    linha.getCodLoja(), linha.getDescrLoja(), linha.getMatricula(),
                    linha.getDataAdmissao(), linha.getDataDemissao(),
                    linha.getCodCargo(), linha.getDescrCargo(), linha.getNumeroLinha()
            );
            erros.addAll(hrRowValidator.validar(hrRow));
        } else if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
            Map<String, Registration> colaboradores = Collections.emptyMap();
            if (linha.getMatricula() != null && !linha.getMatricula().isBlank()) {
                var optReg = registrationRepository.findByRegistrationIgnoreCase(linha.getMatricula().trim());
                if (optReg.isPresent()) {
                    colaboradores = Map.of(optReg.get().getRegistration().toUpperCase(), optReg.get());
                }
            }

            SalesFileRow salesRow = new SalesFileRow(
                    linha.getDataReferencia(), linha.getCodMarca(), linha.getDescrMarca(),
                    linha.getCodLoja(), linha.getDescrLoja(), linha.getMatricula(),
                    linha.getValorVenda(), linha.getNumeroLinha()
            );
            erros.addAll(salesRowValidator.validar(salesRow, compYm, colaboradores, null));
        }

        String severidadeMax = calcularSeveridadeMaxima(erros);
        boolean isValid = erros.stream().noneMatch(e -> e.severidade() == ImportInconsistencySeverity.IMPEDITIVO
                || e.severidade() == ImportInconsistencySeverity.ADVERTENCIA);

        linha.setStatusLinha(isValid ? "VALIDA" : "INVALIDA");
        linha.setSeveridadeMaxima(severidadeMax);
        linha.setErrosValidacaoJson(toJson(erros));
        linha.setCamposComErro(erros.stream().map(ErroValidacaoLinhaDTO::campo).distinct().collect(Collectors.joining(",")));
    }

    private void executarRevalidacaoCompleta(LotePreparacao lote) {
        List<LinhaPreparacao> linhas = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(lote.getId());

        Set<String> matriculas = linhas.stream()
                .map(LinhaPreparacao::getMatricula)
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase())
                .collect(Collectors.toSet());

        Set<String> matriculasDuplicadas = linhas.stream()
                .filter(l -> !l.isIgnorarNaEfetivacao())
                .map(LinhaPreparacao::getMatricula)
                .filter(Objects::nonNull)
                .map(m -> m.trim().toUpperCase())
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Map<String, Registration> colaboradoresBanco = Collections.emptyMap();
        if (!matriculas.isEmpty()) {
            colaboradoresBanco = registrationRepository.findByRegistrationIn(matriculas).stream()
                    .collect(Collectors.toMap(r -> r.getRegistration().toUpperCase(), r -> r));
        }

        Set<String> chavesVendas = new HashSet<>();
        YearMonth compYm = lote.getCompetencia() != null ? YearMonth.parse(lote.getCompetencia()) : null;

        int validas = 0;
        int invalidas = 0;
        int impeditivas = 0;
        int advertencias = 0;
        int avisos = 0;
        int editadas = 0;
        int ignoradas = 0;

        for (LinhaPreparacao linha : linhas) {
            if (linha.isEditada()) editadas++;
            if (linha.isIgnorarNaEfetivacao()) ignoradas++;

            List<ErroValidacaoLinhaDTO> erros = new ArrayList<>();

            if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
                HrFileRow hrRow = new HrFileRow(
                        linha.getDataReferencia(), linha.getCodMarca(), linha.getDescrMarca(),
                        linha.getCodLoja(), linha.getDescrLoja(), linha.getMatricula(),
                        linha.getDataAdmissao(), linha.getDataDemissao(),
                        linha.getCodCargo(), linha.getDescrCargo(), linha.getNumeroLinha()
                );
                erros.addAll(hrRowValidator.validar(hrRow, matriculasDuplicadas, colaboradoresBanco));
            } else if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
                SalesFileRow salesRow = new SalesFileRow(
                        linha.getDataReferencia(), linha.getCodMarca(), linha.getDescrMarca(),
                        linha.getCodLoja(), linha.getDescrLoja(), linha.getMatricula(),
                        linha.getValorVenda(), linha.getNumeroLinha()
                );
                erros.addAll(salesRowValidator.validar(salesRow, compYm, colaboradoresBanco, chavesVendas));
                if (linha.getMatricula() != null && linha.getDataReferencia() != null && linha.getCodLoja() != null && linha.getCodMarca() != null && linha.getValorVenda() != null) {
                    chavesVendas.add(String.format("%s:%s:%d:%d:%s", linha.getMatricula().trim(), linha.getDataReferencia(), linha.getCodLoja(), linha.getCodMarca(), linha.getValorVenda()));
                }
            }

            String severidadeMax = calcularSeveridadeMaxima(erros);
            boolean isValid = erros.stream().noneMatch(e -> e.severidade() == ImportInconsistencySeverity.IMPEDITIVO
                    || e.severidade() == ImportInconsistencySeverity.ADVERTENCIA);

            if (isValid) {
                validas++;
            } else {
                invalidas++;
            }

            if ("IMPEDITIVO".equals(severidadeMax) && !linha.isIgnorarNaEfetivacao()) impeditivas++;
            if ("ADVERTENCIA".equals(severidadeMax)) advertencias++;
            if ("AVISO".equals(severidadeMax)) avisos++;

            linha.setStatusLinha(isValid ? "VALIDA" : "INVALIDA");
            linha.setSeveridadeMaxima(severidadeMax);
            linha.setErrosValidacaoJson(toJson(erros));
            linha.setCamposComErro(erros.stream().map(ErroValidacaoLinhaDTO::campo).distinct().collect(Collectors.joining(",")));
        }

        linhaPreparacaoRepository.saveAll(linhas);

        lote.setTotalLinhas(linhas.size());
        lote.setLinhasValidas(validas);
        lote.setLinhasInvalidas(invalidas);
        lote.setLinhasImpeditivas(impeditivas);
        lote.setLinhasAdvertencia(advertencias);
        lote.setLinhasAviso(avisos);
        lote.setLinhasEditadas(editadas);
        lotePreparacaoRepository.save(lote);
    }

    private void atualizarContadoresLote(LotePreparacao lote) {
        List<LinhaPreparacao> linhas = linhaPreparacaoRepository.findByLotePreparacaoIdOrderByNumeroLinhaAsc(lote.getId());
        int validas = 0;
        int invalidas = 0;
        int impeditivas = 0;
        int advertencias = 0;
        int avisos = 0;
        int editadas = 0;

        for (LinhaPreparacao l : linhas) {
            if ("VALIDA".equals(l.getStatusLinha())) validas++;
            else invalidas++;

            if ("IMPEDITIVO".equals(l.getSeveridadeMaxima()) && !l.isIgnorarNaEfetivacao()) impeditivas++;
            if ("ADVERTENCIA".equals(l.getSeveridadeMaxima())) advertencias++;
            if ("AVISO".equals(l.getSeveridadeMaxima())) avisos++;
            if (l.isEditada()) editadas++;
        }

        lote.setTotalLinhas(linhas.size());
        lote.setLinhasValidas(validas);
        lote.setLinhasInvalidas(invalidas);
        lote.setLinhasImpeditivas(impeditivas);
        lote.setLinhasAdvertencia(advertencias);
        lote.setLinhasAviso(avisos);
        lote.setLinhasEditadas(editadas);
        lotePreparacaoRepository.save(lote);
    }

    private String calcularSeveridadeMaxima(List<ErroValidacaoLinhaDTO> erros) {
        if (erros == null || erros.isEmpty()) return null;
        if (erros.stream().anyMatch(e -> e.severidade() == ImportInconsistencySeverity.IMPEDITIVO)) {
            return "IMPEDITIVO";
        }
        if (erros.stream().anyMatch(e -> e.severidade() == ImportInconsistencySeverity.ADVERTENCIA)) {
            return "ADVERTENCIA";
        }
        if (erros.stream().anyMatch(e -> e.severidade() == ImportInconsistencySeverity.AVISO)) {
            return "AVISO";
        }
        return null;
    }

    private String validarTipoBase(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            throw new BusinessException("Tipo de base é obrigatório.");
        }
        String t = tipo.trim().toUpperCase();
        if ("COMISSIONS".equals(t) || "COMMISSIONS".equals(t)) {
            throw new BusinessException("A área de preparação suporta apenas as bases de RH e Vendas. A base de taxas (COMISSIONS) deve ser importada diretamente.");
        }
        if (!"HR".equals(t) && !"SALES".equals(t)) {
            throw new BusinessException("Tipo de base inválido: '" + tipo + "'. Permitidos: HR, SALES.");
        }
        return t;
    }

    private String validarCompetencia(String tipoBase, String competencia) {
        if ("HR".equalsIgnoreCase(tipoBase)) {
            return null;
        }
        if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            throw new BusinessException("Parâmetro 'competencia' é obrigatório no formato YYYY-MM para base de Vendas.");
        }
        return competencia;
    }

    private void validarVigenciaCampanhaEPeriodoDisponivel(Campanha campanha, String competencia) {
        if (competencia == null) return;
        CampanhaVersao versao = campanha.getVersaoVigente() != null ? campanha.getVersaoVigente() : campanha.getVersaoProposta();
        YearMonth ym = YearMonth.parse(competencia);
        if (versao != null) {
            List<YearMonth> vigentes = coberturaFechamentoService.listarCompetenciasInterseccao(versao.getDataInicio(), versao.getDataFim());
            if (!vigentes.contains(ym)) {
                throw new BusinessException(String.format(
                        "Competência %s não intersecta o período de vigência da campanha (%s a %s).",
                        competencia, versao.getDataInicio(), versao.getDataFim()
                ));
            }
        } else if (campanha.getDataInicio() != null && campanha.getDataFim() != null) {
            List<YearMonth> vigentes = coberturaFechamentoService.listarCompetenciasInterseccao(campanha.getDataInicio(), campanha.getDataFim());
            if (!vigentes.contains(ym)) {
                throw new BusinessException(String.format(
                        "Competência %s não intersecta o período de vigência da campanha (%s a %s).",
                        competencia, campanha.getDataInicio(), campanha.getDataFim()
                ));
            }
        }
        fechamentoService.validarPeriodosDisponiveis(ym.atDay(1), ym.atEndOfMonth());
    }

    private Campanha buscarCampanha(Long campanhaId) {
        return campanhaRepository.findByIdAndRemovidoEmIsNull(campanhaId)
                .or(() -> campanhaRepository.findById(campanhaId).filter(c -> c.getRemovidoEm() == null))
                .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada: " + campanhaId));
    }

    private LotePreparacao buscarLote(UUID preparacaoId, Long campanhaId) {
        return lotePreparacaoRepository.findByIdAndCampanhaId(preparacaoId, campanhaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de preparação não encontrado: " + preparacaoId));
    }

    private String calcularHashSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao calcular hash SHA-256 do arquivo.", ex);
        }
    }

    private Map<String, Object> extrairValoresEditaveis(LinhaPreparacao l) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dataReferencia", l.getDataReferencia());
        map.put("codMarca", l.getCodMarca());
        map.put("descrMarca", l.getDescrMarca());
        map.put("codLoja", l.getCodLoja());
        map.put("descrLoja", l.getDescrLoja());
        map.put("matricula", l.getMatricula());
        map.put("dataAdmissao", l.getDataAdmissao());
        map.put("dataDemissao", l.getDataDemissao());
        map.put("codCargo", l.getCodCargo());
        map.put("descrCargo", l.getDescrCargo());
        map.put("valorVenda", l.getValorVenda());
        map.put("ignorarNaEfetivacao", l.isIgnorarNaEfetivacao());
        map.put("registroAlvoId", l.getRegistroAlvoId());
        return map;
    }

    private Map<String, Object> extrairValoresRegistroRh(Registration r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("matricula", r.getRegistration());
        map.put("codLoja", r.getStore() != null ? r.getStore().getCode() : null);
        map.put("codCargo", r.getPosition() != null ? r.getPosition().getCode() : null);
        map.put("dataAdmissao", r.getAdmissDate());
        map.put("dataDemissao", r.getDemissDate());
        map.put("versao", r.getVersao());
        return map;
    }

    private Map<String, Object> extrairValoresRegistroVenda(Sale s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("matricula", s.getRegistration() != null ? s.getRegistration().getRegistration() : null);
        map.put("dataVenda", s.getSaleDate());
        map.put("codLoja", s.getStore() != null ? s.getStore().getCode() : null);
        map.put("codMarca", s.getBrand() != null ? s.getBrand().getCode() : null);
        map.put("valorVenda", s.getValue());
        map.put("versao", s.getVersao());
        return map;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<ErroValidacaoLinhaDTO> fromJsonListaErros(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ErroValidacaoLinhaDTO>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private LotePreparacaoResponseDTO toLoteDTO(LotePreparacao l) {
        int linhasIgnoradas = linhaPreparacaoRepository.countByLotePreparacaoIdAndIgnorarNaEfetivacaoTrue(l.getId());
        ResumoContagensPreparacaoDTO contagens = new ResumoContagensPreparacaoDTO(
                l.getTotalLinhas(),
                l.getLinhasValidas(),
                l.getLinhasInvalidas(),
                l.getLinhasImpeditivas(),
                l.getLinhasAdvertencia(),
                l.getLinhasAviso(),
                l.getLinhasEditadas(),
                linhasIgnoradas
        );

        Long envioOrigemReportId = l.getEnvioArquivoOrigem() != null ? l.getEnvioArquivoOrigem().getReportId() : null;
        Long envioEfetivadoReportId = l.getEnvioArquivoEfetivado() != null ? l.getEnvioArquivoEfetivado().getReportId() : null;

        return new LotePreparacaoResponseDTO(
                l.getId(),
                l.getCampanha().getId(),
                l.getTipoBase(),
                l.getCompetencia(),
                l.getNomeArquivo(),
                l.getHashConteudo(),
                l.getArquivoImportacao().getId(),
                envioOrigemReportId,
                envioEfetivadoReportId,
                l.getStatus(),
                l.getOrigemCriacao(),
                l.getVersaoPreparo(),
                l.isAptaParaEfetivacao(),
                contagens,
                l.getCriadoEm(),
                l.getCriadoPor(),
                l.getRevalidadoEm(),
                l.getEfetivadoEm(),
                l.getEfetivadoPor(),
                l.getDescartadoEm(),
                l.getDescartadoPor(),
                l.getMotivoDescarte()
        );
    }

    private LinhaPreparacaoResponseDTO toLinhaDTO(LinhaPreparacao lp) {
        LinhaPreparacaoResponseDTO.DadosInterpretadosDTO dados = new LinhaPreparacaoResponseDTO.DadosInterpretadosDTO(
                lp.getDataReferencia(),
                lp.getCodMarca(),
                lp.getDescrMarca(),
                lp.getCodLoja(),
                lp.getDescrLoja(),
                lp.getMatricula(),
                lp.getDataAdmissao(),
                lp.getDataDemissao(),
                lp.getCodCargo(),
                lp.getDescrCargo(),
                lp.getValorVenda()
        );

        return new LinhaPreparacaoResponseDTO(
                lp.getId(),
                lp.getNumeroLinha(),
                lp.getVersao(),
                lp.getStatusLinha(),
                "VALIDA".equals(lp.getStatusLinha()),
                lp.getSeveridadeMaxima(),
                lp.isEditada(),
                lp.getOrigemUltimaEdicao(),
                lp.isIgnorarNaEfetivacao(),
                lp.getRegistroAlvoId(),
                fromJsonMap(lp.getConteudoOriginalJson()),
                dados,
                fromJsonListaErros(lp.getErrosValidacaoJson()),
                lp.getAtualizadoEm(),
                lp.getAtualizadoPor()
        );
    }

    private HistoricoEdicaoLinhaResponseDTO toHistoricoEdicaoDTO(HistoricoEdicaoPreparacao h) {
        return new HistoricoEdicaoLinhaResponseDTO(
                h.getId(),
                h.getNumeroLinha(),
                h.getVersaoAnterior(),
                h.getVersaoNova(),
                h.getOrigem(),
                h.getCamposAlterados(),
                h.getValoresAnterioresJson(),
                h.getValoresNovosJson(),
                h.getErrosAntesJson(),
                h.getErrosDepoisJson(),
                h.getMotivo(),
                h.getEditadoPor(),
                h.getEditadoEm()
        );
    }

    private HistoricoVersaoRegistroResponseDTO toHistoricoVersaoDTO(HistoricoVersaoRegistro h) {
        return new HistoricoVersaoRegistroResponseDTO(
                h.getId(),
                h.getTipoBase(),
                h.getRegistroId(),
                h.getChaveNegocio(),
                h.getVersao(),
                h.getTipoOperacao(),
                h.getOrigem(),
                h.getCampanha() != null ? h.getCampanha().getId() : null,
                h.getLotePreparacao() != null ? h.getLotePreparacao().getId() : null,
                h.getIdLote(),
                h.getValoresAnterioresJson(),
                h.getValoresNovosJson(),
                h.getRegistradoPor(),
                h.getRegistradoEm()
        );
    }
}
