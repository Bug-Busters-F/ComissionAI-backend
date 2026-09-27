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
 * Ordem de prioridade e precedência definida:
 * 1. Regra de negócio ativa em tb_regra (Campanhas ativas, promoções temporárias, regras específicas por canal/loja/vendedor).
 *    A regra ativa sobrepõe e substitui a taxa base da competência.
 *    Em caso de múltiplas regras ativas aplicáveis, avalia-se o grau de especificidade:
 *    - Matrícula específica (peso 16) > Loja específica (peso 8) > Cargo específico (peso 4) > Canal específico (peso 2) > Marca geral (peso 1).
 *    - Caso haja empate na maior especificidade com múltiplas regras concorrentes, a resolução é bloqueada por ambiguidade/conflito.
 * 2. Taxa base cadastrada em tb_basecomiss (Fallback para taxa padrão por Marca + Cargo do colaborador na competência).
 * 
 * Caso nenhum percentual seja localizado, os vínculos essenciais apresentem inconsistências ou ocorra conflito de regras,
 * o cálculo é marcado como IMPEDIDO com motivo descritivo contratual.
 * Percentuais nunca são somados implicitamente.
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

        // 1ª Prioridade: Buscar regra ativa em tb_regra (Campanhas promocionais ou regras específicas)
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
            if (regras.size() == 1) {
                Regra regra = regras.get(0);
                String versaoRegra = "REGRA#" + regra.getId() + " - " + regra.getNome();
                return ResolucaoTaxaResult.sucesso(regra.getTaxa(), regra.getId(), "REGRA_NEGOCIO", versaoRegra, null);
            }

            // Múltiplas regras aplicáveis: aplicar critério determinístico por nível de especificidade
            int maiorEspecificidade = regras.stream()
                    .mapToInt(this::calcularEspecificidade)
                    .max()
                    .orElse(0);

            List<Regra> regrasMaisEspecificas = regras.stream()
                    .filter(r -> calcularEspecificidade(r) == maiorEspecificidade)
                    .toList();

            // Empate na maior especificidade com múltiplas regras concorrentes: conflito/ambiguidade bloqueado
            if (regrasMaisEspecificas.size() > 1) {
                List<Long> idsConflitantes = regrasMaisEspecificas.stream().map(Regra::getId).toList();
                log.warn("Conflito de regras ativas para a venda ID {}: múltiplas regras no nível de especificidade {} (IDs: {})",
                        sale.getId(), maiorEspecificidade, idsConflitantes);
                return ResolucaoTaxaResult.impedido(String.format(
                        "Conflito de regras: múltiplas regras ativas concorrentes encontradas com o mesmo nível de especificidade (Regras IDs: %s). Resolução bloqueada por ambiguidade.",
                        idsConflitantes
                ));
            }

            Regra regraVencedora = regrasMaisEspecificas.get(0);
            String versaoRegra = "REGRA#" + regraVencedora.getId() + " - " + regraVencedora.getNome();
            return ResolucaoTaxaResult.sucesso(regraVencedora.getTaxa(), regraVencedora.getId(), "REGRA_NEGOCIO", versaoRegra, null);
        }

        // 2ª Prioridade (Fallback): Buscar taxa padrão em tb_basecomiss (Marca + Cargo)
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
            String referencia = "BASE_COMISS (Marca: " + (brand.getCode() != null ? brand.getCode() : brand.getId())
                    + ", Cargo: " + (position.getCode() != null ? position.getCode() : position.getId()) + ")";
            return ResolucaoTaxaResult.sucesso(taxa, REGRA_BASE_ID, "BASE_COMISS", referencia, null);
        }

        // Se nem regra nem basecomiss forem encontradas: impedimento
        return ResolucaoTaxaResult.impedido(String.format(
                "Taxa de comissão não encontrada: nenhuma regra ativa aplicável em tb_regra nem taxa padrão cadastrada em tb_basecomiss (Marca: %s, Cargo: %s) para a venda",
                brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                position.getDescription() != null ? position.getDescription() : position.getCode()
        ));
    }

    /**
     * Calcula o peso/nível de especificidade da regra.
     * Regras mais restritas/específicas possuem precedência superior determinística:
     * - Matrícula (colaborador individual): 16
     * - Loja: 8
     * - Cargo: 4
     * - Canal específico (diferente de PADRAO ou nulo): 2
     * - Marca: 1
     */
    private int calcularEspecificidade(Regra regra) {
        int peso = 0;
        if (regra.getMatricula() != null && !regra.getMatricula().isBlank()) {
            peso += 16;
        }
        if (regra.getCodLoja() != null) {
            peso += 8;
        }
        if (regra.getCodCargo() != null) {
            peso += 4;
        }
        if (regra.getCanal() != null && !regra.getCanal().isBlank() && !"PADRAO".equalsIgnoreCase(regra.getCanal())) {
            peso += 2;
        }
        if (regra.getCodMarca() != null) {
            peso += 1;
        }
        return peso;
    }
}
