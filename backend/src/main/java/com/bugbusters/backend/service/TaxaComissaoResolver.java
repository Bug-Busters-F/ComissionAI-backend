package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;

/**
 * Componente responsável pela resolução de taxa de comissão e validação de vínculos essenciais (S1-B08).
 * <p>
 * Ordem de prioridade definida:
 * 1. Taxa base cadastrada em tb_basecomiss (cruzamento Marca + Cargo do colaborador).
 * 2. Regra de negócio ativa em tb_regra (fallback para promoções/campanhas ou regras específicas).
 * Caso nenhum percentual seja localizado ou os vínculos essenciais apresentem inconsistências,
 * o cálculo é marcado como IMPEDIDO com motivo descritivo.
 * </p>
 */
@Component
public class TaxaComissaoResolver {

    private static final Logger log = LoggerFactory.getLogger(TaxaComissaoResolver.class);
    private static final Long REGRA_BASE_ID = 1L;

    private final BaseComissRepository baseComissRepository;
    private final RegraRepository regraRepository;

    public TaxaComissaoResolver(BaseComissRepository baseComissRepository, RegraRepository regraRepository) {
        this.baseComissRepository = baseComissRepository;
        this.regraRepository = regraRepository;
    }

    public ResolucaoTaxaResult resolverTaxa(Sale sale) {
        if (sale == null) {
            return ResolucaoTaxaResult.impedido("Dados da venda não informados");
        }

        Registration registration = sale.getRegistration();
        if (registration == null || registration.getRegistration() == null || registration.getRegistration().isBlank()) {
            return ResolucaoTaxaResult.impedido("Venda sem vínculo com colaborador (matrícula ausente ou não efetivada)");
        }

        Position position = registration.getPosition();
        if (position == null) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Colaborador de matrícula '%s' sem cargo vinculado",
                    registration.getRegistration()
            ));
        }

        LocalDate saleDate = sale.getSaleDate();
        if (saleDate == null) {
            return ResolucaoTaxaResult.impedido("Data da venda não informada");
        }

        if (registration.getAdmissDate() != null && saleDate.isBefore(registration.getAdmissDate())) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Data da venda (%s) é anterior à admissão do colaborador (%s)",
                    saleDate, registration.getAdmissDate()
            ));
        }

        if (registration.getDemissDate() != null && saleDate.isAfter(registration.getDemissDate())) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Data da venda (%s) é posterior à demissão do colaborador (%s)",
                    saleDate, registration.getDemissDate()
            ));
        }

        Brand brand = sale.getBrand();
        if (brand == null) {
            return ResolucaoTaxaResult.impedido("Venda sem marca vinculada");
        }

        if (sale.getValue() == null || sale.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            return ResolucaoTaxaResult.impedido("Valor da venda inválido: deve ser estritamente positivo");
        }

        // 1ª Prioridade: Buscar taxa em tb_basecomiss (Marca + Cargo)
        LocalDate mesRef = saleDate.withDayOfMonth(1);
        Optional<BaseComiss> baseComissOpt = Optional.empty();

        if (brand.getId() != null && position.getId() != null) {
            baseComissOpt = baseComissRepository.findFirstByBrandIdAndPositionIdAndReferenceMonth(
                    brand.getId(), position.getId(), mesRef
            );
            if (baseComissOpt.isEmpty()) {
                baseComissOpt = baseComissRepository.findFirstByBrandIdAndPositionIdOrderByReferenceMonthDesc(
                        brand.getId(), position.getId()
                );
            }
        }

        if (baseComissOpt.isEmpty() && brand.getCode() != null && position.getCode() != null) {
            baseComissOpt = baseComissRepository.findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(
                    brand.getCode(), position.getCode()
            );
        }

        if (baseComissOpt.isPresent() && baseComissOpt.get().getPercentage() != null) {
            BigDecimal taxa = baseComissOpt.get().getPercentage();
            return ResolucaoTaxaResult.sucesso(taxa, REGRA_BASE_ID, "BASE_COMISS");
        }

        // 2ª Prioridade (Fallback): Buscar regra ativa em tb_regra
        Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : null;
        String canal = sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO";

        List<Regra> regras = regraRepository.findRegrasAplicaveis(
                saleDate,
                brand.getCode(),
                codLoja,
                position.getCode(),
                registration.getRegistration(),
                canal,
                StatusRegra.ATIVA
        );

        if (!regras.isEmpty()) {
            Regra regra = regras.get(0);
            return ResolucaoTaxaResult.sucesso(regra.getTaxa(), regra.getId(), "REGRA_NEGOCIO");
        }

        // Se nem basecomiss nem regra forem encontradas: impedimento
        return ResolucaoTaxaResult.impedido(String.format(
                "Taxa de comissão não encontrada em tb_basecomiss (Marca: %s, Cargo: %s) nem regra ativa aplicável em tb_regra para a venda",
                brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                position.getDescription() != null ? position.getDescription() : position.getCode()
        ));
    }
}
