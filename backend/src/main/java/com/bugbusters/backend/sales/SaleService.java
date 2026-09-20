package com.bugbusters.backend.sales;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationResolver;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;

@Service
public class SaleService {

    private final SaleRepository vendaRepository;
    private final RegistrationResolver registrationResolver;
    private final BrandResolver brandResolver;
    private final StoreResolver storeResolver;

    public SaleService(
            SaleRepository vendaRepository,
            RegistrationResolver registrationResolver,
            BrandResolver brandResolver,
            StoreResolver storeResolver) {
        this.vendaRepository = vendaRepository;
        this.registrationResolver = registrationResolver;
        this.brandResolver = brandResolver;
        this.storeResolver = storeResolver;
    }

    @Transactional
    public SaleResponseDTO registrarVenda(SaleRequestDTO request) {

        Registration registration = registrationResolver.resolve(request.registrationCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Matrícula não encontrada: " + request.registrationCode()));

        Brand brand = brandResolver.resolve(request.brandCode());

        Store store = storeResolver.resolve(request.storeCode());

        Sale sale = new Sale();

        sale.setRegistration(registration);
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setValue(request.value());
        sale.setSaleDate(request.saleDate());
        sale.setSaleChannel(request.saleChannel());

        Sale createdSale = vendaRepository.save(sale);

        return mapearParaResponse(createdSale);
    }

    private SaleResponseDTO mapearParaResponse(Sale sale) {

        return new SaleResponseDTO(
                sale.getId(),
                sale.getRegistration(),
                sale.getBrand(),
                sale.getStore(),
                sale.getSaleDate(),
                sale.getValue(),
                sale.getSaleChannel(),
                sale.getCreatedAt());
    }
}