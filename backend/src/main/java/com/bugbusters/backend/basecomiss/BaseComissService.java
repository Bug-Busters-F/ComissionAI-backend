package com.bugbusters.backend.basecomiss;

import com.bugbusters.backend.dto.basecomiss.BaseComissResponseDTO;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BaseComissService {

    private final BaseComissRepository repository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final TravaCompetencia travaCompetencia;

    @Autowired
    public BaseComissService(BaseComissRepository repository,
                             ResultadoCalculoRepository resultadoCalculoRepository,
                             LogCalculoRepository logCalculoRepository,
                             @Autowired(required = false) TravaCompetencia travaCompetencia) {
        this.repository = repository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.travaCompetencia = travaCompetencia;
    }

    public Page<BaseComissResponseDTO> listar(Pageable pageable, Integer codMarca, Integer codCargo, LocalDate mesReferencia) {
        return repository.findByFiltros(codMarca, codCargo, mesReferencia, pageable)
                .map(this::toResponse);
    }

    public List<BaseComissResponseDTO> listarTodas(Integer codMarca, Integer codCargo) {
        return repository.findAllByFiltros(codMarca, codCargo).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<BaseComissResponseDTO> consultarTaxaBase(Integer codMarca, Integer codCargo, LocalDate mesReferencia) {
        if (codMarca == null || codCargo == null) {
            return Optional.empty();
        }

        if (mesReferencia != null) {
            LocalDate primeiroDia = mesReferencia.withDayOfMonth(1);
            Optional<BaseComiss> exato = repository.findByFiltros(codMarca, codCargo, primeiroDia, Pageable.unpaged())
                    .stream().findFirst();
            if (exato.isPresent()) {
                return exato.map(this::toResponse);
            }
        }

        return repository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(codMarca, codCargo)
                .map(this::toResponse);
    }

    public BaseComissResponseDTO toResponse(BaseComiss b) {
        if (b == null) return null;
        Integer codMarca = b.getBrand() != null ? b.getBrand().getCode() : null;
        String descrMarca = b.getBrand() != null ? b.getBrand().getDescription() : null;
        Integer codCargo = b.getPosition() != null ? b.getPosition().getCode() : null;
        String descriCargo = b.getPosition() != null ? b.getPosition().getDescription() : null;

        return new BaseComissResponseDTO(
                b.getId(),
                codMarca,
                descrMarca,
                codCargo,
                descriCargo,
                b.getPercentage(),
                b.getReferenceMonth(),
                b.getIdLoteOrigem()
        );
    }

    /**
     * Exclui um registro de taxa de comissão base pelo ID.
     *
     * @param id Identificador UUID da base de comissão
     * @throws ResourceNotFoundException se a taxa base não for encontrada
     * @throws ResourceConflictException se houver cálculos ou logs dependentes na competência
     */
    @Transactional
    public void delete(UUID id) {
        BaseComiss baseComiss = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Taxa base não encontrada: " + id));

        LocalDate refMonth = baseComiss.getReferenceMonth();
        if (refMonth != null && travaCompetencia != null) {
            travaCompetencia.adquirirTrava(YearMonth.from(refMonth).toString());
        }

        Integer codMarca = baseComiss.getBrand() != null ? baseComiss.getBrand().getCode() : null;
        Integer codCargo = baseComiss.getPosition() != null ? baseComiss.getPosition().getCode() : null;

        if (codMarca != null && codCargo != null && refMonth != null) {
            LocalDate inicio = refMonth.withDayOfMonth(1);
            LocalDate fim = refMonth.withDayOfMonth(refMonth.lengthOfMonth());
            YearMonth ym = YearMonth.from(refMonth);

            if (resultadoCalculoRepository != null) {
                long totalCalculos = resultadoCalculoRepository.countByMarcaCargoEPeriodo(codMarca, codCargo, inicio, fim);
                if (totalCalculos > 0) {
                    throw new ResourceConflictException(String.format(
                            "Não é possível excluir a taxa base %s: existem %d cálculo(s) realizados na competência %s para a marca %d e cargo %d dependentes dela.",
                            id, totalCalculos, ym, codMarca, codCargo));
                }
            }

            if (logCalculoRepository != null) {
                long totalLogs = logCalculoRepository.countByMarcaCargoEPeriodo(codMarca, codCargo, inicio, fim);
                if (totalLogs > 0) {
                    throw new ResourceConflictException(String.format(
                            "Não é possível excluir a taxa base %s: existem %d log(s) financeiros na competência %s para a marca %d e cargo %d dependentes dela.",
                            id, totalLogs, ym, codMarca, codCargo));
                }
            }
        }

        repository.delete(baseComiss);
    }
}
