package com.bugbusters.backend.basecomiss;

import com.bugbusters.backend.dto.basecomiss.BaseComissResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BaseComissService {

    private final BaseComissRepository repository;

    public BaseComissService(BaseComissRepository repository) {
        this.repository = repository;
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
                b.getReferenceMonth()
        );
    }
}
