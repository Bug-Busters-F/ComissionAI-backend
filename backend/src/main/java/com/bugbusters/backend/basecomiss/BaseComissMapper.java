package com.bugbusters.backend.basecomiss;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.importbase.dto.CommissFileRow;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.position.PositionResolver;

@Component
public class BaseComissMapper {

    private final BrandResolver brandResolver;
    private final PositionResolver positionResolver;

    public BaseComissMapper(
            BrandResolver brandResolver,
            PositionResolver positionResolver
    ) {
        this.brandResolver = brandResolver;
        this.positionResolver = positionResolver;
    }

    public BaseComiss toEntity(CommissFileRow row, java.time.LocalDate referenceMonth) {
        if (referenceMonth == null) {
            throw new com.bugbusters.backend.exception.BusinessException("Mês de referência (competência) é obrigatório.");
        }
        Brand brand = brandResolver.resolveOrCreate(row.getBrandCode(), row.getBrandDescription());
        Position position = positionResolver.resolveOrCreate(row.getPositionCode(), row.getPositionDescription());

        return new BaseComiss(
                brand,
                position,
                row.getPercentage(),
                referenceMonth
        );
    }

    @Deprecated
    public BaseComiss toEntity(CommissFileRow row) {
        return toEntity(row, java.time.LocalDate.now().withDayOfMonth(1));
    }
}
