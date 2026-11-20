package com.bugbusters.backend.importbase;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.auth.Usuario;
import com.bugbusters.backend.auth.UsuarioRepository;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.dto.auditoria.AuditoriaEventoContexto;
import com.bugbusters.backend.dto.auditoria.AuditoriaResponseDTO;
import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import com.bugbusters.backend.dto.preparacao.ArquivoOriginalDownloadDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.importbase.batch.ImportJobListener;
import com.bugbusters.backend.importbase.dependencia.DependenciaMotivoDTO;
import com.bugbusters.backend.importbase.dependencia.VerificadorDependenciaLote;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteResponseDTO;
import com.bugbusters.backend.importbase.dto.InconsistenciaLoteDTO;
import com.bugbusters.backend.importbase.dto.LoteImportacaoResponseDTO;
import com.bugbusters.backend.model.ArquivoImportacao;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.InconsistenciaImportacao;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.InconsistenciaImportacaoRepository;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.AuditoriaGeralService;
import com.bugbusters.backend.service.auth.UsuarioAtualProvider;
import com.bugbusters.backend.service.lock.TravaCompetencia;

@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private final JobOperator jobOperator;
    private final Job job;
    private final JobRepository repository;
    private final ImportJobListener listener;
    private final EnvioArquivoRepository envioArquivoRepository;
    private final EnvioArquivoTxService envioArquivoTxService;
    private final TravaCompetencia travaCompetencia;
    private final List<VerificadorDependenciaLote> verificadores;
    private final SaleRepository saleRepository;
    private final RegistrationRepository registrationRepository;
    private final BaseComissRepository baseComissRepository;
    private final com.bugbusters.backend.repository.ArquivoImportacaoRepository arquivoImportacaoRepository;

    @Autowired(required = false)
    private AuditoriaGeralService auditoriaService;

    @Autowired(required = false)
    private UsuarioAtualProvider usuarioAtualProvider;

    @Autowired(required = false)
    private UsuarioRepository usuarioRepository;

    @Autowired(required = false)
    private InconsistenciaImportacaoRepository inconsistenciaImportacaoRepository;

    @Autowired
    public ImportService(@Qualifier("importJobOperator") JobOperator jobOperator,
                         @Qualifier("importJob") Job job,
                         JobRepository repository,
                         ImportJobListener listener,
                         EnvioArquivoRepository envioArquivoRepository,
                         EnvioArquivoTxService envioArquivoTxService,
                         @Autowired(required = false) TravaCompetencia travaCompetencia,
                         List<VerificadorDependenciaLote> verificadores,
                         SaleRepository saleRepository,
                         RegistrationRepository registrationRepository,
                         BaseComissRepository baseComissRepository,
                         @Autowired(required = false) com.bugbusters.backend.repository.ArquivoImportacaoRepository arquivoImportacaoRepository) {
        this.jobOperator = jobOperator;
        this.job = job;
        this.repository = repository;
        this.listener = listener;
        this.envioArquivoRepository = envioArquivoRepository;
        this.envioArquivoTxService = envioArquivoTxService;
        this.travaCompetencia = travaCompetencia;
        this.verificadores = verificadores != null ? verificadores : List.of();
        this.saleRepository = saleRepository;
        this.registrationRepository = registrationRepository;
        this.baseComissRepository = baseComissRepository;
        this.arquivoImportacaoRepository = arquivoImportacaoRepository;
    }

    public ImportResponse processImport(MultipartFile file, ImportType importType) {
        return processImport(file, importType, null);
    }

    public ImportResponse processImport(MultipartFile file, ImportType importType, String competencia) {
        if (file == null || file.isEmpty() || importType == null) {
            throw new BusinessException("Informe um arquivo não vazio e o tipo de importação.");
        }
        if (importType == ImportType.COMISSIONS) {
            if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new BusinessException("Parâmetro 'competencia' é obrigatório no formato YYYY-MM para importação de comissões.");
            }
        } else if (importType == ImportType.SALES) {
            if (competencia == null || !competencia.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                throw new BusinessException("Parâmetro 'competencia' é obrigatório no formato YYYY-MM para importação de vendas.");
            }
        } else if (importType == ImportType.HR) {
            competencia = null;
        }

        String hashConteudo = calcularHashSha256(file);

        // Validação de bloqueio de reenvio do mesmo arquivo enquanto houver lote ativo
        List<EnvioArquivo> lotesAtivos = envioArquivoRepository.findLotesAtivosPorHash(
                hashConteudo, importType.name(), competencia);
        for (EnvioArquivo candidato : lotesAtivos) {
            if (ImportStatus.PENDENTE.name().equals(candidato.getStatus())
                    || ImportStatus.PROCESSANDO.name().equals(candidato.getStatus())) {
                reconciliarLote(candidato);
            }
            if (ImportStatus.PENDENTE.name().equals(candidato.getStatus())
                    || ImportStatus.PROCESSANDO.name().equals(candidato.getStatus())
                    || ImportStatus.CONCLUIDO.name().equals(candidato.getStatus())) {
                String idRef = candidato.getReportId() != null
                        ? "reportId=" + candidato.getReportId()
                        : "idLote=" + candidato.getIdLote();
                throw new ResourceConflictException(String.format(
                        "Já existe um lote ativo (%s, status: %s) para este mesmo arquivo e competência.",
                        idRef, candidato.getStatus()
                ));
            }
        }

        String idLote = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename();
        String criadoPor = resolverUsuario(null);
        String perfilCriador = resolverPerfilUsuario(criadoPor);

        UUID arquivoImportacaoId = null;
        if (arquivoImportacaoRepository != null) {
            try {
                var opt = arquivoImportacaoRepository.findByHashConteudo(hashConteudo);
                if (opt.isPresent()) {
                    arquivoImportacaoId = opt.get().getId();
                } else {
                    ArquivoImportacao novoArq = new ArquivoImportacao(
                            hashConteudo,
                            filename,
                            file.getContentType(),
                            file.getSize(),
                            file.getBytes(),
                            criadoPor
                    );
                    arquivoImportacaoId = arquivoImportacaoRepository.save(novoArq).getId();
                }
            } catch (Exception ignored) {}
        }

        // 1. Gravar lote PENDENTE em transação própria (REQUIRES_NEW) e confirmada antes de disparar o job
        envioArquivoTxService.criarLotePendente(
                idLote, filename, importType, competencia, hashConteudo, arquivoImportacaoId, criadoPor, perfilCriador
        );

        Path upload = null;
        boolean submitted = false;
        try {
            upload = Files.createTempFile("commission-import-", ".upload");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, upload, StandardCopyOption.REPLACE_EXISTING);
            }
            var parametersBuilder = new JobParametersBuilder()
                    .addString("requestId", idLote)
                    .addString("importType", importType.name())
                    .addString("filename", filename, false)
                    .addString("filePath", upload.toString(), false)
                    .addString("criadoPor", criadoPor, false);
            if (perfilCriador != null) {
                parametersBuilder.addString("perfilCriador", perfilCriador, false);
            }
            if (arquivoImportacaoId != null) {
                parametersBuilder.addString("arquivoImportacaoId", arquivoImportacaoId.toString(), false);
            }
            if (competencia != null) {
                parametersBuilder.addString("competencia", competencia);
            }
            var parameters = parametersBuilder.toJobParameters();

            var execution = jobOperator.start(job, parameters);
            submitted = true;
            long reportId = execution.getId();

            // Vincula o report_id ao id_lote
            envioArquivoTxService.vincularReportId(idLote, reportId);

            // Executor rejection does not invoke afterJob; its execution is marked FAILED.
            if (execution.getStatus() == BatchStatus.FAILED) {
                String msgFalha = "Execução rejeitada ou fila cheia.";
                envioArquivoTxService.atualizarStatusFalhou(reportId, idLote, msgFalha, OffsetDateTime.now());
                registrarAuditoriaFalhaInicial(idLote, reportId, filename, importType, competencia,
                        arquivoImportacaoId, criadoPor, perfilCriador, msgFalha);
                listener.deleteUpload(upload);
            }
            return getReport(reportId);
        } catch (BusinessException ex) {
            envioArquivoTxService.atualizarStatusFalhouPorIdLote(idLote, ex.getMessage(), OffsetDateTime.now());
            registrarAuditoriaFalhaInicial(idLote, null, filename, importType, competencia,
                    arquivoImportacaoId, criadoPor, perfilCriador, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            envioArquivoTxService.atualizarStatusFalhouPorIdLote(idLote, ex.getMessage(), OffsetDateTime.now());
            registrarAuditoriaFalhaInicial(idLote, null, filename, importType, competencia,
                    arquivoImportacaoId, criadoPor, perfilCriador, ex.getMessage());
            throw new IllegalStateException("Não foi possível iniciar a importação.", ex);
        } finally {
            if (!submitted && upload != null) {
                listener.deleteUpload(upload);
            }
        }
    }

    public ImportResponse getReport(long reportId) {
        EnvioArquivo lote = envioArquivoRepository.findByReportId(reportId).orElse(null);
        if (lote != null) {
            reconciliarLote(lote);
            ImportStatus status = ImportStatus.valueOf(lote.getStatus());
            Long count = null;
            if (status == ImportStatus.CONCLUIDO) {
                count = (long) lote.getLinhasValidas();
            } else if (status == ImportStatus.FALHOU || status == ImportStatus.EXCLUIDO) {
                count = 0L;
            }
            return new ImportResponse(reportId, lote.getNomeArquivo(),
                    ImportType.valueOf(lote.getTipoBase()), status, count, lote.getMensagem());
        }

        JobExecution execution;
        try {
            execution = repository.getJobExecution(reportId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Relatório de importação não encontrado: " + reportId);
        }
        if (execution == null || !job.getName().equals(execution.getJobInstance().getJobName())) {
            throw new ResourceNotFoundException("Relatório de importação não encontrado: " + reportId);
        }
        ImportStatus status = switch (execution.getStatus()) {
            case STARTING -> ImportStatus.PENDENTE;
            case STARTED, STOPPING -> ImportStatus.PROCESSANDO;
            case COMPLETED -> ImportStatus.CONCLUIDO;
            case FAILED, STOPPED, ABANDONED, UNKNOWN -> ImportStatus.FALHOU;
        };
        Long count = null;
        if (status == ImportStatus.CONCLUIDO) {
            count = execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
        } else if (status == ImportStatus.FALHOU) {
            count = 0L;
        }
        String message = status == ImportStatus.FALHOU
                ? execution.getExecutionContext().getString("failureMessage", "Não foi possível concluir a importação.")
                : execution.getExecutionContext().getString("warningMessage", null);
        return new ImportResponse(reportId, execution.getJobParameters().getString("filename"),
                ImportType.valueOf(execution.getJobParameters().getString("importType")), status, count, message);
    }

    public LoteImportacaoResponseDTO consultarLote(long reportId) {
        EnvioArquivo lote = envioArquivoRepository.findByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado: " + reportId));
        reconciliarLote(lote);
        return hidratarLotes(List.of(lote), true).get(0);
    }

    public Page<LoteImportacaoResponseDTO> listarLotes(String tipoBase, String status, String competencia, Pageable pageable) {
        return listarLotes(tipoBase, status, competencia, null, null, null, null, null, null, pageable);
    }

    public Page<LoteImportacaoResponseDTO> listarLotes(
            String tipoBase,
            String status,
            String competencia,
            String usuario,
            String origemLote,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            Boolean somenteComInconsistencias,
            String busca,
            Pageable pageable
    ) {
        if (dataInicio != null && dataFim != null && dataInicio.isAfter(dataFim)) {
            throw new BusinessException("Data inicial não pode ser posterior à data final.");
        }

        Pageable efetivo = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "criadoEm"))
                : pageable;

        Specification<EnvioArquivo> spec = criarSpecificationLotes(
                tipoBase, status, competencia, usuario, origemLote, dataInicio, dataFim, somenteComInconsistencias, busca
        );

        Page<EnvioArquivo> page = envioArquivoRepository.findAll(spec, efetivo);
        List<LoteImportacaoResponseDTO> dtos = hidratarLotes(page.getContent(), false);
        return new PageImpl<>(dtos, efetivo, page.getTotalElements());
    }

    public Page<InconsistenciaLoteDTO> listarValidacoesLote(
            long reportId,
            String severidade,
            String campo,
            Pageable pageable
    ) {
        EnvioArquivo lote = envioArquivoRepository.findByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado: " + reportId));
        reconciliarLote(lote);

        if (inconsistenciaImportacaoRepository == null) {
            return Page.empty(pageable);
        }

        Pageable efetivo = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("linha"), Sort.Order.asc("id")))
                : pageable;

        Specification<InconsistenciaImportacao> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("envio").get("id"), lote.getId()));
            if (severidade != null && !severidade.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("severidade")), severidade.trim().toUpperCase()));
            }
            if (campo != null && !campo.isBlank()) {
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("campo"), "")), "%" + campo.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return inconsistenciaImportacaoRepository.findAll(spec, efetivo)
                .map(inc -> new InconsistenciaLoteDTO(
                        inc.getId(),
                        lote.getReportId(),
                        lote.getIdLote(),
                        inc.getLinha(),
                        inc.getCampo(),
                        inc.getMotivo(),
                        inc.getSeveridade(),
                        inc.getCriadoEm()
                ));
    }

    @Transactional(readOnly = true)
    public ArquivoOriginalDownloadDTO baixarArquivoOriginal(long reportId) {
        EnvioArquivo lote = envioArquivoRepository.findByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado: " + reportId));
        if (lote.getArquivoImportacaoId() == null || arquivoImportacaoRepository == null) {
            throw new ResourceNotFoundException("Arquivo original não está disponível para o lote: " + reportId);
        }
        ArquivoImportacao arq = arquivoImportacaoRepository.findById(lote.getArquivoImportacaoId())
                .orElseThrow(() -> new ResourceNotFoundException("Conteúdo do arquivo original não encontrado para o lote: " + reportId));
        return new ArquivoOriginalDownloadDTO(
                arq.getNomeArquivo(),
                arq.getContentType(),
                arq.getTamanhoBytes(),
                arq.getConteudo()
        );
    }

    @Transactional
    public ExclusaoLoteResponseDTO excluirLote(long reportId, String usuario, String motivo) {
        EnvioArquivo lote = envioArquivoRepository.findByReportIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado: " + reportId));

        // 1. Reconciliação com Spring Batch
        reconciliarLote(lote);

        // 2. Se estiver PENDENTE ou PROCESSANDO após reconciliação, verificar JobExecution real
        if (ImportStatus.PENDENTE.name().equals(lote.getStatus()) || ImportStatus.PROCESSANDO.name().equals(lote.getStatus())) {
            JobExecution execution = null;
            try {
                execution = repository.getJobExecution(reportId);
            } catch (Exception ignored) {}

            if (execution != null) {
                BatchStatus bs = execution.getStatus();
                if (bs == BatchStatus.STARTING || bs == BatchStatus.STARTED || bs == BatchStatus.STOPPING) {
                    throw new ResourceConflictException("Não é possível excluir o lote " + reportId + ": o processamento da importação ainda está em andamento (" + lote.getStatus() + ").");
                }
            } else {
                throw new ResourceConflictException("Não é possível excluir o lote " + reportId + ": o processamento da importação ainda está em andamento (" + lote.getStatus() + ").");
            }
        }

        // 3. Se já estiver EXCLUIDO
        if (ImportStatus.EXCLUIDO.name().equals(lote.getStatus())) {
            throw new ResourceConflictException("O lote de importação " + reportId + " já foi excluído anteriormente.");
        }

        String usuarioEfetivo = resolverUsuario(usuario);
        String perfilExclusao = resolverPerfilUsuario(usuarioEfetivo);
        String motivoLimpo = (motivo != null && !motivo.isBlank()) ? motivo.trim() : null;
        String statusAnterior = lote.getStatus();

        // 4. Se estiver FALHOU: permite exclusão lógica sem remover registros nem perder validações/mensagem original
        if (ImportStatus.FALHOU.name().equals(statusAnterior)) {
            lote.setStatus(ImportStatus.EXCLUIDO.name());
            lote.setExcluidoEm(OffsetDateTime.now());
            lote.setExcluidoPor(usuarioEfetivo);
            lote.setPerfilExclusao(perfilExclusao);
            lote.setMotivoExclusao(motivoLimpo);
            if (motivoLimpo != null && (lote.getMensagem() == null || lote.getMensagem().isBlank())) {
                lote.setMensagem(motivoLimpo);
            }
            envioArquivoRepository.save(lote);
            UUID protocolo = registrarAuditoriaExclusao(lote, statusAnterior, 0L, usuarioEfetivo, perfilExclusao, motivoLimpo);
            return new ExclusaoLoteResponseDTO(reportId, ImportStatus.EXCLUIDO.name(), 0L, lote.getExcluidoEm(),
                    usuarioEfetivo, motivoLimpo, protocolo);
        }

        // 5. Adquirir trava de competência se SALES ou COMISSIONS
        if (("SALES".equalsIgnoreCase(lote.getTipoBase()) || "COMISSIONS".equalsIgnoreCase(lote.getTipoBase()))
                && lote.getCompetencia() != null && travaCompetencia != null) {
            travaCompetencia.adquirirTrava(lote.getCompetencia());
        }

        // 6. Validação de dependências acumuladas
        List<DependenciaMotivoDTO> impedimentos = new ArrayList<>();
        for (VerificadorDependenciaLote verificador : verificadores) {
            impedimentos.addAll(verificador.verificar(lote));
        }

        if (!impedimentos.isEmpty()) {
            List<CampoInvalidoDTO> validacoes = impedimentos.stream()
                    .map(imp -> new CampoInvalidoDTO(imp.campo(), imp.motivo()))
                    .toList();
            throw new ResourceConflictException("Operação de exclusão bloqueada devido a dependências ativas.", validacoes);
        }

        // 7. Remoção atômica em lote com conferência prévia da contagem
        String idLote = lote.getIdLote();
        long esperado = 0;
        int removido = 0;

        if ("SALES".equalsIgnoreCase(lote.getTipoBase())) {
            esperado = saleRepository.countByIdLoteOrigem(idLote);
            removido = saleRepository.deleteByIdLoteOrigem(idLote);
        } else if ("HR".equalsIgnoreCase(lote.getTipoBase())) {
            esperado = registrationRepository.countByIdLoteOrigem(idLote);
            removido = registrationRepository.deleteByIdLoteOrigem(idLote);
        } else if ("COMISSIONS".equalsIgnoreCase(lote.getTipoBase())) {
            esperado = baseComissRepository.countByIdLoteOrigem(idLote);
            removido = baseComissRepository.deleteByIdLoteOrigem(idLote);
        }

        if (removido != esperado) {
            throw new IllegalStateException(String.format(
                    "Inconsistência transacional na exclusão do lote %s: esperado %d registros, mas foram removidos %d.",
                    idLote, esperado, removido
            ));
        }

        // 8. Atualização do lote para EXCLUIDO mantendo histórico permanente de validações e arquivo
        lote.setStatus(ImportStatus.EXCLUIDO.name());
        lote.setExcluidoEm(OffsetDateTime.now());
        lote.setExcluidoPor(usuarioEfetivo);
        lote.setPerfilExclusao(perfilExclusao);
        lote.setMotivoExclusao(motivoLimpo);
        if (motivoLimpo != null && (lote.getMensagem() == null || lote.getMensagem().isBlank())) {
            lote.setMensagem(motivoLimpo);
        }
        envioArquivoRepository.save(lote);

        UUID protocolo = registrarAuditoriaExclusao(lote, statusAnterior, removido, usuarioEfetivo, perfilExclusao, motivoLimpo);
        return new ExclusaoLoteResponseDTO(reportId, ImportStatus.EXCLUIDO.name(), removido, lote.getExcluidoEm(),
                usuarioEfetivo, motivoLimpo, protocolo);
    }

    private void reconciliarLote(EnvioArquivo lote) {
        if (ImportStatus.PENDENTE.name().equals(lote.getStatus()) || ImportStatus.PROCESSANDO.name().equals(lote.getStatus())) {
            if (lote.getReportId() != null) {
                try {
                    JobExecution execution = repository.getJobExecution(lote.getReportId());
                    if (execution != null && (execution.getEndTime() != null || execution.getStartTime() == null)) {
                        BatchStatus bs = execution.getStatus();
                        if (bs == BatchStatus.COMPLETED) {
                            long writeCount = execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
                            long readCount = execution.getStepExecutions().stream().mapToLong(StepExecution::getReadCount).sum();
                            lote.setStatus(ImportStatus.CONCLUIDO.name());
                            lote.setTotalLinhas((int) (readCount > 0 ? readCount : writeCount));
                            lote.setLinhasValidas((int) writeCount);
                            lote.setRejeicaoIntegral(false);
                            String warningMsg = execution.getExecutionContext().getString("warningMessage", null);
                            if (warningMsg != null && !warningMsg.isBlank()) {
                                lote.setMensagem(warningMsg);
                            }
                            if (execution.getEndTime() != null) {
                                lote.setFinalizadoEm(execution.getEndTime().atOffset(ZoneOffset.UTC));
                            }
                            envioArquivoRepository.save(lote);
                        } else if (bs == BatchStatus.FAILED || bs == BatchStatus.STOPPED
                                || bs == BatchStatus.ABANDONED || bs == BatchStatus.UNKNOWN) {
                            lote.setStatus(ImportStatus.FALHOU.name());
                            lote.setTotalLinhas(0);
                            lote.setLinhasValidas(0);
                            lote.setRejeicaoIntegral(true);
                            String msg = execution.getExecutionContext().getString("failureMessage", "Execução do lote falhou.");
                            lote.setMensagem(msg);
                            if (execution.getEndTime() != null) {
                                lote.setFinalizadoEm(execution.getEndTime().atOffset(ZoneOffset.UTC));
                            }
                            envioArquivoRepository.save(lote);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private Specification<EnvioArquivo> criarSpecificationLotes(
            String tipoBase,
            String status,
            String competencia,
            String usuario,
            String origemLote,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            Boolean somenteComInconsistencias,
            String busca
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tipoBase != null && !tipoBase.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("tipoBase")), tipoBase.trim().toUpperCase()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (competencia != null && !competencia.isBlank()) {
                predicates.add(cb.equal(root.get("competencia"), competencia.trim()));
            }
            if (origemLote != null && !origemLote.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("origemLote")), origemLote.trim().toUpperCase()));
            }
            if (usuario != null && !usuario.isBlank()) {
                String uTermo = "%" + usuario.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("criadoPor"), "")), uTermo),
                        cb.like(cb.lower(cb.coalesce(root.get("excluidoPor"), "")), uTermo)
                ));
            }
            if (dataInicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), dataInicio));
            }
            if (dataFim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("criadoEm"), dataFim));
            }
            if (somenteComInconsistencias != null && query != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<InconsistenciaImportacao> incRoot = sub.from(InconsistenciaImportacao.class);
                sub.select(incRoot.get("id"));
                sub.where(cb.equal(incRoot.get("envio").get("id"), root.get("id")));
                Predicate temRegistro = cb.or(cb.exists(sub), cb.isTrue(root.get("rejeicaoIntegral")));
                predicates.add(somenteComInconsistencias ? temRegistro : cb.not(temRegistro));
            }
            if (busca != null && !busca.isBlank()) {
                String termoStr = busca.trim();
                String termo = "%" + termoStr.toLowerCase() + "%";
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(cb.coalesce(root.get("nomeArquivo"), "")), termo));
                ors.add(cb.like(cb.lower(cb.coalesce(root.get("idLote"), "")), termo));
                ors.add(cb.like(cb.lower(cb.coalesce(root.get("criadoPor"), "")), termo));
                ors.add(cb.like(cb.lower(cb.coalesce(root.get("mensagem"), "")), termo));
                ors.add(cb.like(cb.lower(cb.coalesce(root.get("motivoExclusao"), "")), termo));
                try {
                    ors.add(cb.equal(root.get("reportId"), Long.parseLong(termoStr)));
                } catch (NumberFormatException ignored) {}
                predicates.add(cb.or(ors.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<LoteImportacaoResponseDTO> hidratarLotes(List<EnvioArquivo> envios, boolean incluirInconsistencias) {
        if (envios == null || envios.isEmpty()) {
            return List.of();
        }
        List<Long> envioIds = new ArrayList<>();
        Set<Long> relacionadosIds = new HashSet<>();
        for (EnvioArquivo e : envios) {
            if (e.getId() != null) envioIds.add(e.getId());
            if (e.getEnvioAnteriorId() != null) relacionadosIds.add(e.getEnvioAnteriorId());
            if (e.getSubstituidoPorId() != null) relacionadosIds.add(e.getSubstituidoPorId());
        }

        Map<Long, long[]> contadoresPorEnvio = new HashMap<>();
        if (inconsistenciaImportacaoRepository != null && !envioIds.isEmpty()) {
            for (Object[] row : inconsistenciaImportacaoRepository.contarPorSeveridadePorEnvioIds(envioIds)) {
                Long envioId = row[0] != null ? ((Number) row[0]).longValue() : null;
                String sev = row[1] != null ? String.valueOf(row[1]).trim().toUpperCase() : "";
                long qtd = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                if (envioId == null) continue;
                long[] counts = contadoresPorEnvio.computeIfAbsent(envioId, k -> new long[4]);
                counts[0] += qtd; // total
                if (ImportInconsistencySeverity.IMPEDITIVO.name().equals(sev)) counts[1] += qtd;
                else if (ImportInconsistencySeverity.ADVERTENCIA.name().equals(sev)) counts[2] += qtd;
                else counts[3] += qtd;
            }
        }

        Map<Long, Long> reportIdsPorEnvioId = new HashMap<>();
        if (!relacionadosIds.isEmpty()) {
            for (EnvioArquivo rel : envioArquivoRepository.findByIdIn(relacionadosIds)) {
                if (rel.getId() != null && rel.getReportId() != null) {
                    reportIdsPorEnvioId.put(rel.getId(), rel.getReportId());
                }
            }
        }

        List<InconsistenciaLoteDTO> detalheInconsistencias = null;
        if (incluirInconsistencias && envios.size() == 1 && inconsistenciaImportacaoRepository != null && envios.get(0).getId() != null) {
            EnvioArquivo unico = envios.get(0);
            detalheInconsistencias = inconsistenciaImportacaoRepository.findByEnvioIdOrderByLinhaAscIdAsc(unico.getId())
                    .stream()
                    .map(inc -> new InconsistenciaLoteDTO(
                            inc.getId(),
                            unico.getReportId(),
                            unico.getIdLote(),
                            inc.getLinha(),
                            inc.getCampo(),
                            inc.getMotivo(),
                            inc.getSeveridade(),
                            inc.getCriadoEm()
                    ))
                    .toList();
        }

        List<LoteImportacaoResponseDTO> resposta = new ArrayList<>(envios.size());
        for (EnvioArquivo e : envios) {
            long[] counts = e.getId() != null ? contadoresPorEnvio.getOrDefault(e.getId(), new long[4]) : new long[4];
            resposta.add(new LoteImportacaoResponseDTO(
                    e.getReportId(),
                    e.getIdLote(),
                    e.getNomeArquivo(),
                    e.getTipoBase(),
                    e.getCompetencia(),
                    e.getStatus(),
                    e.getTotalLinhas(),
                    e.getMensagem(),
                    e.getCriadoEm(),
                    e.getIniciadoEm(),
                    e.getFinalizadoEm(),
                    e.getExcluidoEm(),
                    e.getExcluidoPor(),
                    e.getLinhasValidas(),
                    e.isRejeicaoIntegral(),
                    e.getHashConteudo(),
                    e.getArquivoImportacaoId(),
                    e.getOrigemLote(),
                    e.getCriadoPor(),
                    e.getPerfilCriador(),
                    e.getMotivoExclusao(),
                    e.getPerfilExclusao(),
                    e.getEnvioAnteriorId() != null ? reportIdsPorEnvioId.get(e.getEnvioAnteriorId()) : null,
                    e.getSubstituidoPorId() != null ? reportIdsPorEnvioId.get(e.getSubstituidoPorId()) : null,
                    counts[0],
                    counts[1],
                    counts[2],
                    counts[3],
                    incluirInconsistencias && envios.size() == 1 ? detalheInconsistencias : null
            ));
        }
        return resposta;
    }

    private String resolverUsuario(String fallback) {
        if (usuarioAtualProvider != null) {
            return usuarioAtualProvider.obterUsuarioAtual(fallback);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario u && u.getEmail() != null && !u.getEmail().isBlank()) {
            return u.getEmail().trim();
        }
        return (fallback != null && !fallback.isBlank()) ? fallback.trim() : "SISTEMA";
    }

    private String resolverPerfilUsuario(String email) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario u && u.getPapel() != null) {
            if ("SISTEMA".equalsIgnoreCase(email) || (u.getEmail() != null && u.getEmail().equalsIgnoreCase(email))) {
                return u.getPapel().name();
            }
        }
        if (usuarioRepository != null && email != null && !"SISTEMA".equalsIgnoreCase(email)) {
            return usuarioRepository.findByEmailIgnoreCase(email.trim())
                    .map(u -> u.getPapel() != null ? u.getPapel().name() : null)
                    .orElse(null);
        }
        return null;
    }

    private void registrarAuditoriaFalhaInicial(
            String idLote,
            Long reportId,
            String filename,
            ImportType importType,
            String competencia,
            UUID arquivoImportacaoId,
            String usuario,
            String perfil,
            String detalheErro
    ) {
        if (auditoriaService == null) {
            return;
        }
        try {
            auditoriaService.registrarEventoIsolado(AuditoriaEventoContexto.builder()
                    .usuario(usuario)
                    .perfilUsuario(perfil)
                    .tipoAcao(TipoAcaoAuditoria.IMPORTACAO_BASE)
                    .tipoEntidade(TipoEntidadeAuditoria.LOTE_IMPORTACAO)
                    .entidadeId(reportId != null ? String.valueOf(reportId) : idLote)
                    .arquivoImportacaoId(arquivoImportacaoId)
                    .reportId(reportId)
                    .idLote(idLote)
                    .nomeArquivo(filename)
                    .competencia(competencia)
                    .descricao(String.format("Falha ao iniciar importação %s (%s): %s",
                            importType != null ? importType.name() : "BASE",
                            filename,
                            detalheErro != null ? detalheErro : "erro no processamento."))
                    .sucesso(false)
                    .detalheErro(detalheErro));
        } catch (Exception ex) {
            log.warn("Falha ao registrar auditoria inicial de erro do lote {}: {}", idLote, ex.getMessage());
        }
    }

    private UUID registrarAuditoriaExclusao(
            EnvioArquivo lote,
            String statusAnterior,
            long registrosRemovidos,
            String usuarioEfetivo,
            String perfilExclusao,
            String motivo
    ) {
        if (auditoriaService == null || lote == null) {
            return null;
        }
        try {
            AuditoriaResponseDTO resp = auditoriaService.registrarEvento(AuditoriaEventoContexto.builder()
                    .usuario(usuarioEfetivo)
                    .perfilUsuario(perfilExclusao)
                    .tipoAcao(TipoAcaoAuditoria.EXCLUSAO_BASE)
                    .tipoEntidade(TipoEntidadeAuditoria.LOTE_IMPORTACAO)
                    .entidadeId(lote.getReportId() != null ? String.valueOf(lote.getReportId()) : lote.getIdLote())
                    .envioArquivoId(lote.getId())
                    .arquivoImportacaoId(lote.getArquivoImportacaoId())
                    .reportId(lote.getReportId())
                    .idLote(lote.getIdLote())
                    .nomeArquivo(lote.getNomeArquivo())
                    .competencia(lote.getCompetencia())
                    .descricao(String.format("Lote %s (%s) excluído com %d registros removidos%s.",
                            lote.getReportId() != null ? "#" + lote.getReportId() : lote.getIdLote(),
                            lote.getNomeArquivo(),
                            registrosRemovidos,
                            motivo != null ? " — motivo: " + motivo : ""))
                    .dadosAnteriores(String.format("{\"status\":\"%s\",\"linhasValidas\":%d}",
                            statusAnterior, lote.getLinhasValidas()))
                    .dadosNovos(String.format("{\"status\":\"EXCLUIDO\",\"registrosRemovidos\":%d,\"motivoExclusao\":\"%s\"}",
                            registrosRemovidos, motivo != null ? motivo.replace("\"", "'") : ""))
                    .sucesso(true));
            return resp != null ? resp.protocolo() : null;
        } catch (Exception ex) {
            log.warn("Falha ao registrar auditoria de exclusão do lote {}: {}", lote.getReportId(), ex.getMessage());
            return null;
        }
    }

    private String calcularHashSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao calcular hash SHA-256 do arquivo.", ex);
        }
    }
}
