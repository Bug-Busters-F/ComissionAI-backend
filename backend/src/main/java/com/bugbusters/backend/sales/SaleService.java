package com.bugbusters.backend.sales;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.brand.BrandResolver;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceConflictException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationResolver;
import com.bugbusters.backend.repository.EnvioArquivoRepository;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.repository.SimulacaoVendaRepository;
import com.bugbusters.backend.sales.dto.SaleRequestDTO;
import com.bugbusters.backend.sales.dto.SaleResponseDTO;
import com.bugbusters.backend.service.lock.TravaCompetencia;
import com.bugbusters.backend.store.Store;
import com.bugbusters.backend.store.StoreResolver;

@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final SaleRepository vendaRepository;
    private final RegistrationResolver registrationResolver;
    private final BrandResolver brandResolver;
    private final StoreResolver storeResolver;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;
    private final SimulacaoVendaRepository simulacaoVendaRepository;
    private final TravaCompetencia travaCompetencia;
    private final EnvioArquivoRepository envioArquivoRepository;

    public SaleService(
            SaleRepository vendaRepository,
            RegistrationResolver registrationResolver,
            BrandResolver brandResolver,
            StoreResolver storeResolver,
            @Autowired(required = false) ResultadoCalculoRepository resultadoCalculoRepository,
            @Autowired(required = false) LogCalculoRepository logCalculoRepository,
            @Autowired(required = false) SimulacaoVendaRepository simulacaoVendaRepository,
            @Autowired(required = false) TravaCompetencia travaCompetencia,
            @Autowired(required = false) EnvioArquivoRepository envioArquivoRepository) {
        this.vendaRepository = vendaRepository;
        this.registrationResolver = registrationResolver;
        this.brandResolver = brandResolver;
        this.storeResolver = storeResolver;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
        this.simulacaoVendaRepository = simulacaoVendaRepository;
        this.travaCompetencia = travaCompetencia;
        this.envioArquivoRepository = envioArquivoRepository;
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
     * Exclui todas as vendas de uma competência específica (mês e ano), validando ausência de cálculos e logs financeiros.
     * Atualiza os lotes de vendas correspondentes para EXCLUIDO.
     */
    @Transactional
    public long deletarVendasPorCompetencia(int mes, int ano) {
        if (mes < 1 || mes > 12 || ano < 1900 || ano > 2100) {
            throw new BusinessException("Mês ou ano de competência inválido: " + mes + "/" + ano);
        }

        String competencia = String.format("%04d-%02d", ano, mes);
        LocalDate inicio = LocalDate.of(ano, mes, 1);
        LocalDate fim = inicio.withDayOfMonth(inicio.lengthOfMonth());

        if (travaCompetencia != null) {
            travaCompetencia.adquirirTrava(competencia);
        }

        List<Sale> vendas = vendaRepository.findBySaleDateBetweenOrderBySaleDateAsc(inicio, fim);
        if (vendas.isEmpty()) {
            return 0;
        }

        List<UUID> idsVendas = vendas.stream().map(Sale::getId).toList();

        if (resultadoCalculoRepository != null && resultadoCalculoRepository.existsByIdVendaIn(idsVendas)) {
            throw new ResourceConflictException(String.format(
                    "Não é possível excluir as vendas da competência %s: existem cálculos vinculados a elas. Remova os cálculos primeiro.",
                    competencia));
        }

        if (logCalculoRepository != null && logCalculoRepository.existsByIdVendaIn(idsVendas)) {
            throw new ResourceConflictException(String.format(
                    "Não é possível excluir as vendas da competência %s: existem logs de auditoria financeira vinculados a elas.",
                    competencia));
        }

        if (simulacaoVendaRepository != null) {
            simulacaoVendaRepository.deleteByIdVendaIn(idsVendas);
        }

        long total = vendas.size();
        vendaRepository.deleteAllInBatch(vendas);

        if (envioArquivoRepository != null) {
            List<EnvioArquivo> lotes = envioArquivoRepository.findByTipoBaseIgnoreCaseAndCompetencia("SALES", competencia);
            OffsetDateTime agora = OffsetDateTime.now();
            for (EnvioArquivo lote : lotes) {
                if (!"EXCLUIDO".equalsIgnoreCase(lote.getStatus())) {
                    lote.setStatus("EXCLUIDO");
                    lote.setExcluidoEm(agora);
                    lote.setExcluidoPor("SISTEMA");
                    lote.setMensagem("Exclusão de vendas da competência " + competencia + " solicitada pelo usuário");
                }
            }
            envioArquivoRepository.saveAll(lotes);
        }

        log.info("Excluídas {} vendas da competência {}.", total, competencia);
        return total;
    }

    /**
     * Exclui todas as vendas cadastradas no sistema, validando ausência de cálculos e logs financeiros vinculados.
     * Atualiza todos os lotes de vendas para EXCLUIDO.
     */
    @Transactional
    public long deletarTodasVendas() {
        if (resultadoCalculoRepository != null) {
            long totalCalculos = resultadoCalculoRepository.count();
            if (totalCalculos > 0) {
                throw new ResourceConflictException(String.format(
                        "Não é possível excluir todas as vendas: existem %d cálculo(s) realizados vinculados a elas no sistema.",
                        totalCalculos));
            }
        }

        if (logCalculoRepository != null) {
            long totalLogs = logCalculoRepository.count();
            if (totalLogs > 0) {
                throw new ResourceConflictException(String.format(
                        "Não é possível excluir todas as vendas: existem %d log(s) de auditoria financeira vinculados a elas no sistema.",
                        totalLogs));
            }
        }

        long total = vendaRepository.count();
        if (total == 0) {
            return 0;
        }

        if (simulacaoVendaRepository != null) {
            simulacaoVendaRepository.deleteAllInBatch();
        }

        vendaRepository.deleteAllInBatch();

        if (envioArquivoRepository != null) {
            List<EnvioArquivo> lotes = envioArquivoRepository.findByTipoBaseIgnoreCase("SALES");
            OffsetDateTime agora = OffsetDateTime.now();
            for (EnvioArquivo lote : lotes) {
                if (!"EXCLUIDO".equalsIgnoreCase(lote.getStatus())) {
                    lote.setStatus("EXCLUIDO");
                    lote.setExcluidoEm(agora);
                    lote.setExcluidoPor("SISTEMA");
                    lote.setMensagem("Exclusão de todas as vendas solicitada pelo usuário");
                }
            }
            envioArquivoRepository.saveAll(lotes);
        }

        log.info("Excluídas todas as {} vendas cadastradas no sistema.", total);
        return total;
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
        sale.setTipoVenda(request.tipoVenda() != null && !request.tipoVenda().isBlank() ? request.tipoVenda() : "INFORMADA");
        sale.setIdLoteOrigem(request.idLoteOrigem());

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

    @Transactional(readOnly = true)
    public SaleResponseDTO buscarVendaPorId(UUID id) {
        Sale sale = vendaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + id));
        return mapearParaResponse(sale);
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
                sale.getCreatedAt(),
                sale.getTipoVenda(),
                sale.getIdLoteOrigem());
    }
}