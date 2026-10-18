package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.basecomiss.ReferenciaFaltanteDTO;
import com.bugbusters.backend.exception.AusenciaCoberturaException;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.registration.RegistrationRepository;
import com.bugbusters.backend.sales.Sale;

@Service
public class CoberturaBaseService {

    private static final Logger log = LoggerFactory.getLogger(CoberturaBaseService.class);

    private final BaseComissRepository baseComissRepository;
    private final RegistrationRepository registrationRepository;

    public CoberturaBaseService(BaseComissRepository baseComissRepository,
                                 RegistrationRepository registrationRepository) {
        this.baseComissRepository = baseComissRepository;
        this.registrationRepository = registrationRepository;
    }

    /**
     * Consulta a taxa base contratual para um par marca/cargo na competência informada.
     * Regra adotada: reference_month estritamente igual ao primeiro dia da competência.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> obterTaxaBase(Integer codMarca, Integer codCargo, YearMonth ym) {
        if (codMarca == null || codCargo == null || ym == null) {
            return Optional.empty();
        }
        LocalDate refMonth = ym.atDay(1);
        return baseComissRepository.findByBrandCodeAndPositionCodeAndReferenceMonth(codMarca, codCargo, refMonth)
                .map(BaseComiss::getPercentage);
    }

    /**
     * Verifica se existe pelo menos um registro de taxa base em tb_basecomiss para a competência informada.
     */
    @Transactional(readOnly = true)
    public boolean possuiBaseCadastradaNaCompetencia(YearMonth ym) {
        if (ym == null) {
            return false;
        }
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();
        return baseComissRepository.countByReferenceMonthBetween(inicio, fim) > 0;
    }

    /**
     * Conta a quantidade de registros de taxa base em tb_basecomiss para a competência informada.
     */
    @Transactional(readOnly = true)
    public long contarBasesNaCompetencia(YearMonth ym) {
        if (ym == null) {
            return 0;
        }
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();
        return baseComissRepository.countByReferenceMonthBetween(inicio, fim);
    }

    /**
     * Lista anos e meses distintos presentes em tb_basecomiss.
     */
    @Transactional(readOnly = true)
    public List<Object[]> findDistinctAnosEMesesBaseComiss() {
        return baseComissRepository.findDistinctAnosEMesesBaseComiss();
    }

    /**
     * (a) Verifica a cobertura de taxa base para uma lista de vendas sem lançar exceção.
     * Retorna a lista de referências faltantes estruturadas.
     * O cargo da venda vem de Sale -> Registration -> Position.
     * Vendas sem matrícula, sem cargo ou sem marca entram como referência faltante/inválida.
     * Todas as bases são consultadas em uma consulta única por competência.
     */
    @Transactional(readOnly = true)
    public List<ReferenciaFaltanteDTO> verificarCobertura(List<Sale> vendas, YearMonth ym) {
        if (vendas == null || vendas.isEmpty() || ym == null) {
            return Collections.emptyList();
        }

        String compStr = ym.toString();
        LocalDate refMonth = ym.atDay(1);

        Map<String, ReferenciaFaltanteDTO> faltantesMap = new LinkedHashMap<>();
        Map<String, ParMarcaCargo> paresValidos = new LinkedHashMap<>();

        for (Sale sale : vendas) {
            Brand brand = sale.getBrand();
            Registration reg = sale.getRegistration();
            Position pos = reg != null ? reg.getPosition() : null;

            Integer codMarca = brand != null ? brand.getCode() : null;
            String descrMarca = brand != null ? brand.getDescription() : null;
            Integer codCargo = pos != null ? pos.getCode() : null;
            String descriCargo = pos != null ? pos.getDescription() : null;

            // Se matrícula, cargo ou marca estiverem ausentes, adiciona como faltante/inválida
            if (reg == null || codMarca == null || codCargo == null) {
                String chaveFaltante = "marca:" + codMarca + "/cargo:" + codCargo;
                faltantesMap.putIfAbsent(chaveFaltante, new ReferenciaFaltanteDTO(
                        codMarca, descrMarca, codCargo, descriCargo, compStr
                ));
            } else {
                String chavePar = chave(codMarca, codCargo);
                paresValidos.putIfAbsent(chavePar, new ParMarcaCargo(codMarca, descrMarca, codCargo, descriCargo));
            }
        }

        // Se há pares válidos, busca todas as bases necessárias em uma consulta única
        if (!paresValidos.isEmpty()) {
            Set<Integer> brandCodes = paresValidos.values().stream()
                    .map(ParMarcaCargo::codMarca)
                    .collect(Collectors.toSet());
            Set<Integer> posCodes = paresValidos.values().stream()
                    .map(ParMarcaCargo::codCargo)
                    .collect(Collectors.toSet());

            List<BaseComiss> bases = baseComissRepository.findByBrandCodeInAndPositionCodeInAndReferenceMonth(
                    brandCodes, posCodes, refMonth
            );

            Set<String> chavesExistentes = bases.stream()
                    .filter(b -> b.getBrand() != null && b.getBrand().getCode() != null
                            && b.getPosition() != null && b.getPosition().getCode() != null
                            && b.getPercentage() != null)
                    .map(b -> chave(b.getBrand().getCode(), b.getPosition().getCode()))
                    .collect(Collectors.toSet());

            for (Map.Entry<String, ParMarcaCargo> entry : paresValidos.entrySet()) {
                if (!chavesExistentes.contains(entry.getKey())) {
                    ParMarcaCargo par = entry.getValue();
                    String chaveFaltante = "marca:" + par.codMarca() + "/cargo:" + par.codCargo();
                    faltantesMap.putIfAbsent(chaveFaltante, new ReferenciaFaltanteDTO(
                            par.codMarca(), par.descrMarca(), par.codCargo(), par.descriCargo(), compStr
                    ));
                }
            }
        }

        return new ArrayList<>(faltantesMap.values());
    }

    /**
     * (b) Valida a cobertura de taxa base para uma lista de vendas.
     * Lança AusenciaCoberturaException caso haja referências faltantes.
     */
    @Transactional(readOnly = true)
    public void validarCobertura(List<Sale> vendas, YearMonth ym) {
        List<ReferenciaFaltanteDTO> faltantes = verificarCobertura(vendas, ym);
        if (!faltantes.isEmpty()) {
            log.warn("Processamento interrompido: ausência de cobertura em tb_basecomiss para a competência {}: {}",
                    ym, faltantes);
            throw new AusenciaCoberturaException(ym, faltantes);
        }
    }

    /**
     * Valida a cobertura para uma venda individual persistida.
     * Lança AusenciaCoberturaException se faltar taxa base na competência da venda.
     */
    @Transactional(readOnly = true)
    public void validarCobertura(Sale venda) {
        if (venda == null || venda.getSaleDate() == null) {
            return;
        }
        YearMonth ym = YearMonth.from(venda.getSaleDate());
        validarCobertura(List.of(venda), ym);
    }

    /**
     * Valida a cobertura para cálculo de venda avulsa informada via DTO (sem entidade Sale persistida).
     * Lança AusenciaCoberturaException se o colaborador/cargo não for encontrado ou se faltar base.
     */
    @Transactional(readOnly = true)
    public void validarCoberturaAvulsa(Integer codMarca, String descrMarca, String matricula, LocalDate dataVenda) {
        if (dataVenda == null) {
            return;
        }
        YearMonth ym = YearMonth.from(dataVenda);
        String compStr = ym.toString();

        Registration reg = (matricula != null && !matricula.isBlank())
                ? registrationRepository.findByRegistration(matricula.trim()).orElse(null)
                : null;
        Position pos = reg != null ? reg.getPosition() : null;

        Integer codCargo = pos != null ? pos.getCode() : null;
        String descriCargo = pos != null ? pos.getDescription() : null;

        if (reg == null || codMarca == null || codCargo == null) {
            throw new AusenciaCoberturaException(ym, List.of(new ReferenciaFaltanteDTO(
                    codMarca, descrMarca, codCargo, descriCargo, compStr
            )));
        }

        Optional<BigDecimal> baseOpt = obterTaxaBase(codMarca, codCargo, ym);
        if (baseOpt.isEmpty()) {
            throw new AusenciaCoberturaException(ym, List.of(new ReferenciaFaltanteDTO(
                    codMarca, descrMarca, codCargo, descriCargo, compStr
            )));
        }
    }

    private static String chave(Integer codMarca, Integer codCargo) {
        return codMarca + ":" + codCargo;
    }

    private record ParMarcaCargo(
            Integer codMarca,
            String descrMarca,
            Integer codCargo,
            String descriCargo
    ) {}
}
