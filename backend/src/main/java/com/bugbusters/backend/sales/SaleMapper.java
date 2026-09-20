package com.bugbusters.backend.sales;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationResolver;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;

@Component
public class SaleMapper {
    private final RegistrationResolver registrationResolver;
    private final BrandResolver brandResolver;
    private final StoreResolver storeResolver;

    public SaleMapper(RegistrationResolver registrationResolver, StoreResolver storeResolver,
            BrandResolver brandResolver) {
        this.registrationResolver = registrationResolver;
        this.storeResolver = storeResolver;
        this.brandResolver = brandResolver;
    }

    public Sale toEntity(SalesFileRow row) {
        Registration registration = registrationResolver.resolve(row.getRegistration());
        Store store = storeResolver.resolveOrCreate(row.getStoreCode(), row.getStoreDescription());
        Brand brand = brandResolver.resolveOrCreate(row.getBrandCode(), row.getBrandDescription());

        Sale saleEntity = new Sale();

        saleEntity.setRegistration(registration);
        saleEntity.setStore(store);
        saleEntity.setBrand(brand);
        saleEntity.setValue(row.getSaleValue());
        saleEntity.setSaleDate(row.getReferenceDate());

        return saleEntity;
    }
}
