package com.bugbusters.backend.importbase;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.dto.error.ApiErrorResponse.CampoInvalidoDTO;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.importbase.batch.ImportJobListener;
import com.bugbusters.backend.importbase.dependencia.DependenciaMotivoDTO;
import com.bugbusters.backend.importbase.dependencia.VerificadorDependenciaLote;
import com.bugbusters.backend.importbase.dto.ExclusaoLoteResponseDTO;
import com.bugbusters.backend.importbase.dto.LoteImportacaoResponseDTO;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;

@Service
public class ImportService {
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

    public ImportService(@Qualifier("importJobOperator") JobOperator jobOperator,
                         @Qualifier("importJob") Job job,
                         JobRepository repository,
                         ImportJobListener listener,
                         EnvioArquivoRepository envioArquivoRepository,
                         EnvioArquivoTxService envioArquivoTxService,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) TravaCompetencia travaCompetencia,
                         List<VerificadorDependenciaLote> verificadores,
                         SaleRepository saleRepository,
                         RegistrationRepository registrationRepository,
                         BaseComissRepository baseComissRepository) {
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

        String idLote = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename() == null ? "arquivo" : file.getOriginalFilename();

        // 1. Gravar lote PENDENTE em transação própria (REQUIRES_NEW) e confirmada antes de disparar o job
        envioArquivoTxService.criarLotePendente(idLote, filename, importType, competencia);

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
                    .addString("filePath", upload.toString(), false);
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
                envioArquivoTxService.atualizarStatusFalhou(reportId, "Execução rejeitada ou fila cheia.", OffsetDateTime.now());
                listener.deleteUpload(upload);
            }
            return getReport(reportId);
        } catch (BusinessException ex) {
            envioArquivoTxService.atualizarStatusFalhouPorIdLote(idLote, ex.getMessage(), OffsetDateTime.now());
            throw ex;
        } catch (Exception ex) {
            envioArquivoTxService.atualizarStatusFalhouPorIdLote(idLote, ex.getMessage(), OffsetDateTime.now());
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
                count = (long) lote.getTotalLinhas();
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
                : null;
        return new ImportResponse(reportId, execution.getJobParameters().getString("filename"),
                ImportType.valueOf(execution.getJobParameters().getString("importType")), status, count, message);
    }

    public LoteImportacaoResponseDTO consultarLote(long reportId) {
        EnvioArquivo lote = envioArquivoRepository.findByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de importação não encontrado: " + reportId));
        reconciliarLote(lote);
        return toDTO(lote);
    }

    public Page<LoteImportacaoResponseDTO> listarLotes(String tipoBase, String status, String competencia, Pageable pageable) {
        String tipoFiltro = tipoBase != null && !tipoBase.isBlank() ? tipoBase.trim().toUpperCase() : null;
        String statusFiltro = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        String compFiltro = competencia != null && !competencia.isBlank() ? competencia.trim() : null;

        return envioArquivoRepository.findByFiltros(tipoFiltro, statusFiltro, compFiltro, pageable)
                .map(this::toDTO);
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

        // 4. Se estiver FALHOU: permite exclusão lógica sem remover registros
        if (ImportStatus.FALHOU.name().equals(lote.getStatus())) {
            lote.setStatus(ImportStatus.EXCLUIDO.name());
            lote.setExcluidoEm(OffsetDateTime.now());
            lote.setExcluidoPor(usuario != null && !usuario.isBlank() ? usuario : "SISTEMA");
            if (motivo != null && !motivo.isBlank()) {
                lote.setMensagem(motivo);
            }
            envioArquivoRepository.save(lote);
            return new ExclusaoLoteResponseDTO(reportId, ImportStatus.EXCLUIDO.name(), 0L, lote.getExcluidoEm());
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

        // 8. Atualização do lote para EXCLUIDO
        lote.setStatus(ImportStatus.EXCLUIDO.name());
        lote.setExcluidoEm(OffsetDateTime.now());
        lote.setExcluidoPor(usuario != null && !usuario.isBlank() ? usuario : "SISTEMA");
        if (motivo != null && !motivo.isBlank()) {
            lote.setMensagem(motivo);
        }
        envioArquivoRepository.save(lote);

        return new ExclusaoLoteResponseDTO(reportId, ImportStatus.EXCLUIDO.name(), removido, lote.getExcluidoEm());
    }

    private void reconciliarLote(EnvioArquivo lote) {
        if (ImportStatus.PENDENTE.name().equals(lote.getStatus()) || ImportStatus.PROCESSANDO.name().equals(lote.getStatus())) {
            if (lote.getReportId() != null) {
                try {
                    JobExecution execution = repository.getJobExecution(lote.getReportId());
                    if (execution != null) {
                        BatchStatus bs = execution.getStatus();
                        if (bs == BatchStatus.COMPLETED) {
                            long writeCount = execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
                            lote.setStatus(ImportStatus.CONCLUIDO.name());
                            lote.setTotalLinhas((int) writeCount);
                            lote.setLinhasValidas((int) writeCount);
                            lote.setRejeicaoIntegral(false);
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

    private LoteImportacaoResponseDTO toDTO(EnvioArquivo e) {
        return new LoteImportacaoResponseDTO(
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
                e.getExcluidoPor()
        );
    }
}
