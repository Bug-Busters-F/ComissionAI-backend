package com.bugbusters.backend.sales;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationResolver;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;

@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

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

    /**
     *
     * @throws ResourceNotFoundException
     */
    @Transactional
    public void deletarVenda(UUID id) {
        if (!vendaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Venda não encontrada: " + id);
        }
        vendaRepository.deleteById(id);
        log.info("Venda ID {} excluída.", id);
    }

    /**
     * Registra uma venda individual aplicando verificação de duplicidade e
     * idempotência.
     *
     * Regras:
     * 1. Se o ID de venda fornecido já existir com todos os dados idênticos, a
     * chamada é considerada
     * um reenvio acidental idempotente e retorna a venda existente sem salvar nova
     * entidade.
     * 2. Se o ID de venda já existir mas com dados divergentes (ex: valor
     * alterado), lança BusinessException.
     * 3. Caso contrário, persiste a nova venda.
     */
    @Transactional
    public SaleResponseDTO registrarVenda(SaleRequestDTO request) {

        if (request.id() != null) {
            Optional<Sale> existenteOpt = vendaRepository.findById(request.id());
            if (existenteOpt.isPresent()) {
                Sale existente = existenteOpt.get();
                validarConsistenciaVenda(request, existente);
                log.info(
                        "Idempotência aplicada para venda ID {}: registro idêntico existente retornado sem duplicidade.",
                        request.id());
                return mapearParaResponse(existente);
            }
        }

        Registration registration = registrationResolver.resolve(request.registrationCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Matrícula não encontrada: " + request.registrationCode()));

        Brand brand = brandResolver.resolve(request.brandCode());
        Store store = storeResolver.resolve(request.storeCode());

        Sale sale = new Sale();
        if (request.id() != null) {
            sale.setId(request.id());
        }
        sale.setRegistration(registration);
        sale.setBrand(brand);
        sale.setStore(store);
        sale.setValue(request.value());
        sale.setSaleDate(request.saleDate());
        sale.setSaleChannel(request.saleChannel());

        Sale createdSale = vendaRepository.save(sale);

        return mapearParaResponse(createdSale);
    }

    private void validarConsistenciaVenda(SaleRequestDTO request, Sale existente) {
        boolean divergente = false;

        if (existente.getRegistration() != null &&
                !existente.getRegistration().getRegistration().equalsIgnoreCase(request.registrationCode())) {
            divergente = true;
        }

        if (existente.getBrand() != null &&
                !existente.getBrand().getCode().equals(request.brandCode())) {
            divergente = true;
        }

        if (existente.getStore() != null &&
                !existente.getStore().getCode().equals(request.storeCode())) {
            divergente = true;
        }

        if (existente.getValue() != null &&
                existente.getValue().compareTo(request.value()) != 0) {
            divergente = true;
        }

        if (existente.getSaleDate() != null &&
                !existente.getSaleDate().equals(request.saleDate())) {
            divergente = true;
        }

        if (existente.getSaleChannel() != null &&
                !existente.getSaleChannel().equalsIgnoreCase(request.saleChannel())) {
            divergente = true;
        }

        if (divergente) {
            throw new BusinessException(String.format(
                    "Solicitação rejeitada por duplicidade com dados divergentes. A venda com ID '%s' já foi cadastrada com dados diferentes.",
                    request.id()));
        }
    }

    public Page<SaleResponseDTO> readAllSales(Pageable pageable) {
        return vendaRepository.findAll(pageable)
                .map(this::mapearParaResponse);
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