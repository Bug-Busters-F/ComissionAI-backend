package com.bugbusters.backend.registration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.registration.dto.RegistrationResponseDTO;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.SaleRepository;

import jakarta.transaction.Transactional;

@Service
public class RegistrationService {

    private final RegistrationRepository repository;
    private final RegistrationMapper mapper;
    private final SaleRepository saleRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final RegraRepository regraRepository;
    private final EnvioArquivoRepository envioArquivoRepository;

    public RegistrationService(
            RegistrationRepository repository,
            RegistrationMapper mapper,
            SaleRepository saleRepository,
            @Autowired(required = false) ResultadoCalculoRepository resultadoCalculoRepository,
            @Autowired(required = false) LogCalculoRepository logCalculoRepository,
            @Autowired(required = false) RegraRepository regraRepository,
            @Autowired(required = false) EnvioArquivoRepository envioArquivoRepository
        ) {
        this.repository = repository;
        this.mapper = mapper;
        this.saleRepository = saleRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.regraRepository = regraRepository;
        this.envioArquivoRepository = envioArquivoRepository;
    }

    public Page<RegistrationResponseDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable)
                .map(mapper::toResponse);
    }

    public Page<RegistrationResponseDTO> findAll(Integer lojaCodigo, Integer cargoCodigo, String matricula, Pageable pageable) {
        if (lojaCodigo == null && cargoCodigo == null && (matricula == null || matricula.isBlank())) {
            return findAll(pageable);
        }
        String matriculaFiltro = (matricula != null && !matricula.isBlank()) ? matricula.trim() : null;
        return repository.findByFiltros(lojaCodigo, cargoCodigo, matriculaFiltro, pageable)
                .map(mapper::toResponse);
    }

    public java.util.Optional<RegistrationResponseDTO> findByRegistration(String registration) {
        if (registration == null || registration.isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findByRegistrationIgnoreCase(registration.trim())
                .map(mapper::toResponse);
    }


    /**
     * Exclui uma matrícula pelo ID.
     *
     * @throws ResourceNotFoundException se a matrícula não existir
     * @throws ResourceConflictException se houver vendas vinculadas a ela
     */
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Matrícula não encontrada: " + id);
        }
        if (saleRepository.existsByRegistrationId(id)) {
            throw new ResourceConflictException(
                    "Não é possível excluir a matrícula " + id + ": existem vendas vinculadas a ela.");
        }
        repository.deleteById(id);
    }

    /**
     * Exclui todas as matrículas cadastradas, validando ausência de vendas, cálculos e logs financeiros.
     * Atualiza os lotes de RH correspondentes para EXCLUIDO.
     */
    @Transactional
    public long deletarTodas() {
        long totalVendas = saleRepository.count();
        if (totalVendas > 0) {
            throw new ResourceConflictException(String.format(
                    "Não é possível excluir todas as matrículas: existem %d venda(s) cadastradas no sistema vinculadas a elas. Exclua as vendas primeiro.",
                    totalVendas));
        }

        if (resultadoCalculoRepository != null) {
            long totalCalculos = resultadoCalculoRepository.count();
            if (totalCalculos > 0) {
                throw new ResourceConflictException(String.format(
                        "Não é possível excluir todas as matrículas: existem %d cálculo(s) realizados vinculados a elas.",
                        totalCalculos));
            }
        }

        if (logCalculoRepository != null) {
            long totalLogs = logCalculoRepository.count();
            if (totalLogs > 0) {
                throw new ResourceConflictException(String.format(
                        "Não é possível excluir todas as matrículas: existem %d log(s) de auditoria financeira vinculados a elas.",
                        totalLogs));
            }
        }

        if (regraRepository != null && regraRepository.existsByMatriculaIsNotNullAndRemovidoEmIsNull()) {
            throw new ResourceConflictException(
                    "Não é possível excluir todas as matrículas: existem regras ativas de campanha vinculadas a matrículas específicas.");
        }

        long totalMatriculas = repository.count();
        repository.deleteAllInBatch();

        if (envioArquivoRepository != null) {
            List<EnvioArquivo> lotesRh = envioArquivoRepository.findByTipoBaseIgnoreCase("HR");
            OffsetDateTime agora = OffsetDateTime.now();
            for (EnvioArquivo lote : lotesRh) {
                if (!"EXCLUIDO".equalsIgnoreCase(lote.getStatus())) {
                    lote.setStatus("EXCLUIDO");
                    lote.setExcluidoEm(agora);
                    lote.setExcluidoPor("SISTEMA");
                    lote.setMensagem("Exclusão de todas as matrículas solicitada pelo usuário");
                }
            }
            envioArquivoRepository.saveAll(lotesRh);
        }

        return totalMatriculas;
    }
}