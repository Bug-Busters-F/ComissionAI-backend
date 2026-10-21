package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.RegraRepository;
import com.bugbusters.backend.sales.Sale;

/**
 * Componente responsável pela resolução de taxa de comissão e validação de vínculos essenciais (S1-B08 / BACK-4).
 * <p>
 * Ordem de prioridade e precedência definida:
 * 1. Regras de negócio ativas em tb_regra (Campanhas ativas, promoções temporárias, regras específicas por canal/loja/vendedor/faixa).
 *    - Filtros de período, dimensões cadastrais e limites de valor (inclusivos/exclusivos) são aplicados rigorosamente
 *      ANTES da seleção da regra vencedora.
 *    - Avaliação de especificidade determinística:
 *      Matrícula específica (peso 16) > Loja específica (peso 8) > Cargo específico (peso 4) > Canal específico (peso 2) > Marca geral (peso 1).
 *    - Caso haja empate na maior especificidade com múltiplas regras ativas concorrentes aplicáveis, a resolução é bloqueada por ambiguidade.
 *    - Suporte a taxas diretas (DEFINIR_TAXA) e ajustes operacionais sobre a base (ACRESCIMO_PONTOS, DESCONTO_PONTOS, MULTIPLICADOR_BASE, DIVISOR_BASE).
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

    private final CoberturaBaseService coberturaBaseService;
    private final RegraRepository regraRepository;
    private final BaseComissRepository baseComissRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public TaxaComissaoResolver(CoberturaBaseService coberturaBaseService,
                                RegraRepository regraRepository,
                                @org.springframework.beans.factory.annotation.Autowired(required = false) BaseComissRepository baseComissRepository) {
        this.coberturaBaseService = coberturaBaseService;
        this.regraRepository = regraRepository;
        this.baseComissRepository = baseComissRepository;
    }

    public TaxaComissaoResolver(CoberturaBaseService coberturaBaseService, RegraRepository regraRepository) {
        this(coberturaBaseService, regraRepository, null);
    }

    /**
     * Contexto de dados pré-carregados para resolução de comissões em lote de uma competência (BACK-14).
     * Elimina consultas repetitivas N+1 ao banco de dados durante fechamentos mensais.
     */
    public record ContextoCompetencia(
            LocalDate dataInicio,
            LocalDate dataFim,
            List<Regra> regrasAtivas,
            List<BaseComiss> basesComissao
    ) {}

    public ContextoCompetencia carregarContexto(LocalDate inicio, LocalDate fim) {
        List<Regra> regras = regraRepository.findRegrasAtivasNoPeriodo(inicio, fim, StatusRegra.ATIVA);
        List<BaseComiss> bases = baseComissRepository.findAllComRelacionamentos();
        return new ContextoCompetencia(inicio, fim, regras, bases);
    }

    public ResolucaoTaxaResult resolverTaxa(Sale sale) {
        return resolverTaxa(sale, null);
    }

    public ResolucaoTaxaResult resolverTaxa(Sale sale, ContextoCompetencia contexto) {
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

        // 1ª Prioridade: Buscar regras ativas em tb_regra (Campanhas promocionais ou regras específicas)
        Integer codLoja = sale.getStore() != null ? sale.getStore().getCode() : null;
        String canal = sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO";

        List<Regra> regras;
        if (contexto != null && contexto.regrasAtivas() != null) {
            regras = contexto.regrasAtivas().stream()
                    .filter(r -> regraAplica(r, saleDate, brand.getCode(), codLoja, position.getCode(), registration.getRegistration(), canal))
                    .toList();
        } else {
            regras = regraRepository.findRegrasAplicaveis(
                    saleDate,
                    brand.getCode(),
                    codLoja,
                    position.getCode(),
                    registration.getRegistration(),
                    canal,
                    StatusRegra.ATIVA
            );
        }

        // 1.1 Exclui registro técnico padrão usado como referência para taxa base
        regras = regras.stream()
                .filter(regra -> !isRegraPadraoTecnica(regra))
                .toList();

        // 1.2 Aplica os filtros de faixa de valor monetário rigorosamente ANTES de escolher a regra vencedora
        regras = regras.stream()
                .filter(regra -> atendeFaixaValor(regra, sale.getValue()))
                .toList();

        if (!regras.isEmpty()) {
            if (regras.size() == 1) {
                return resolverTaxaRegraVencedora(regras.get(0), sale, contexto);
            }

            // Múltiplas regras aplicáveis: aplicar critério determinístico por nível de especificidade
            int maiorEspecificidade = regras.stream()
                    .mapToInt(this::calcularEspecificidade)
                    .max()
                    .orElse(0);

            List<Regra> regrasMaisEspecificas = regras.stream()
                    .filter(r -> calcularEspecificidade(r) == maiorEspecificidade)
                    .toList();

            // Desempate adicional: com a mesma especificidade, a regra que restringe o valor por faixa
            // prevalece sobre a que não possui faixa. Os pesos documentados não são alterados.
            if (regrasMaisEspecificas.size() > 1) {
                List<Regra> comFaixa = regrasMaisEspecificas.stream().filter(Regra::possuiFaixa).toList();
                if (!comFaixa.isEmpty() && comFaixa.size() < regrasMaisEspecificas.size()) {
                    regrasMaisEspecificas = comFaixa;
                }
            }

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
            return resolverTaxaRegraVencedora(regraVencedora, sale, contexto);
        }

        // 2ª Prioridade (Fallback): Buscar taxa padrão em tb_basecomiss (Marca + Cargo na competência)
        Optional<BigDecimal> baseRateOpt = buscarTaxaBase(brand, position, saleDate, contexto);

        if (baseRateOpt.isPresent()) {
            BigDecimal taxa = baseRateOpt.get();
            String referencia = "BASE_COMISS (Marca: " + (brand.getCode() != null ? brand.getCode() : brand.getId())
                    + ", Cargo: " + (position.getCode() != null ? position.getCode() : position.getId()) + ")";
            return ResolucaoTaxaResult.sucesso(taxa, REGRA_BASE_ID, "BASE_COMISS", referencia, null);
        }

        // Se nem regra nem basecomiss forem encontradas: impedimento contratual
        return ResolucaoTaxaResult.impedido(String.format(
                "Taxa de comissão não encontrada: nenhuma regra ativa aplicável em tb_regra nem taxa padrão cadastrada em tb_basecomiss (Marca: %s, Cargo: %s) para a venda",
                brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                position.getDescription() != null ? position.getDescription() : position.getCode()
        ));
    }

    /**
     * Valida se o valor da venda atende aos limites monetários (faixa) configurados na regra.
     * Respeita os modificadores minInclusivo e maxInclusivo (assumindo true como padrão quando nulos).
     */
    private boolean atendeFaixaValor(Regra regra, BigDecimal valorVenda) {
        if (regra == null || valorVenda == null) {
            return false;
        }

        if (regra.getValorMinimo() != null) {
            boolean minInclusivo = regra.getMinInclusivo() == null || regra.getMinInclusivo();
            int compMin = valorVenda.compareTo(regra.getValorMinimo());
            if (minInclusivo ? compMin < 0 : compMin <= 0) {
                return false;
            }
        }

        if (regra.getValorMaximo() != null) {
            boolean maxInclusivo = regra.getMaxInclusivo() == null || regra.getMaxInclusivo();
            int compMax = valorVenda.compareTo(regra.getValorMaximo());
            if (maxInclusivo ? compMax > 0 : compMax >= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Resolve e valida a taxa final da regra vencedora, aplicando ajustes sobre a base quando aplicável.
     */
    private ResolucaoTaxaResult resolverTaxaRegraVencedora(Regra regra, Sale sale) {
        return resolverTaxaRegraVencedora(regra, sale, null);
    }

    private ResolucaoTaxaResult resolverTaxaRegraVencedora(Regra regra, Sale sale, ContextoCompetencia contexto) {
        TipoOperacaoBase operacao = regra.getTipoOperacao() != null
                ? regra.getTipoOperacao()
                : TipoOperacaoBase.DEFINIR_TAXA;

        // Caso 1: Taxa fixa definida diretamente
        if (operacao == TipoOperacaoBase.DEFINIR_TAXA) {
            BigDecimal taxa = regra.getTaxa() != null ? regra.getTaxa() : regra.getValorAjuste();
            if (taxa == null || taxa.compareTo(BigDecimal.ZERO) <= 0) {
                return ResolucaoTaxaResult.impedido(String.format(
                        "Taxa de comissão inválida na regra ID %d ('%s'): percentual não definido ou menor/igual a zero.",
                        regra.getId(), regra.getNome()
                ));
            }
            if (taxa.compareTo(BigDecimal.ONE) > 0) {
                return ResolucaoTaxaResult.impedido(String.format(
                        "Taxa de comissão inválida na regra ID %d ('%s'): percentual (%s) superior a 100%%.",
                        regra.getId(), regra.getNome(), taxa
                ));
            }
            String versaoRegra = "REGRA#" + regra.getId() + " - " + regra.getNome();
            return sucessoPorRegra(regra, taxa.setScale(4, RoundingMode.HALF_UP), versaoRegra);
        }

        // Caso 2: Operações de ajuste sobre a taxa base contratual (tb_basecomiss)
        Brand brand = sale.getBrand();
        Position position = sale.getRegistration().getPosition();
        LocalDate saleDate = sale.getSaleDate();

        Optional<BigDecimal> baseRateOpt = buscarTaxaBase(brand, position, saleDate, contexto);
        if (baseRateOpt.isEmpty()) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Impedimento de cálculo: a regra ID %d ('%s') requer taxa base contratual para o ajuste (%s), mas nenhuma taxa padrão foi encontrada em tb_basecomiss para Marca '%s' e Cargo '%s' na competência.",
                    regra.getId(),
                    regra.getNome(),
                    operacao,
                    brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                    position.getDescription() != null ? position.getDescription() : position.getCode()
            ));
        }

        BigDecimal taxaBase = baseRateOpt.get();
        BigDecimal valorAjuste = regra.getValorAjuste();
        if (valorAjuste == null) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Impedimento de cálculo: a regra ID %d ('%s') com operação '%s' não possui valor de ajuste definido.",
                    regra.getId(), regra.getNome(), operacao
            ));
        }

        BigDecimal taxaCalculada;
        switch (operacao) {
            case ACRESCIMO_PONTOS:
                taxaCalculada = taxaBase.add(valorAjuste);
                break;
            case DESCONTO_PONTOS:
                taxaCalculada = taxaBase.subtract(valorAjuste);
                break;
            case MULTIPLICADOR_BASE:
                taxaCalculada = taxaBase.multiply(valorAjuste);
                break;
            case DIVISOR_BASE:
                if (valorAjuste.compareTo(BigDecimal.ZERO) == 0) {
                    return ResolucaoTaxaResult.impedido(String.format(
                            "Impedimento de cálculo: divisão por zero na operação DIVISOR_BASE da regra ID %d ('%s').",
                            regra.getId(), regra.getNome()
                    ));
                }
                if (valorAjuste.compareTo(BigDecimal.ZERO) < 0) {
                    return ResolucaoTaxaResult.impedido(String.format(
                            "Impedimento de cálculo: fator divisor negativo na operação DIVISOR_BASE da regra ID %d ('%s').",
                            regra.getId(), regra.getNome()
                    ));
                }
                taxaCalculada = taxaBase.divide(valorAjuste, 4, RoundingMode.HALF_UP);
                break;
            default:
                taxaCalculada = taxaBase;
                break;
        }

        taxaCalculada = taxaCalculada.setScale(4, RoundingMode.HALF_UP);

        if (taxaCalculada.compareTo(BigDecimal.ZERO) <= 0) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Impedimento de cálculo: a regra ID %d ('%s') com ajuste %s resultou em taxa percentual inválida (%s). A taxa final deve ser estritamente positiva.",
                    regra.getId(), regra.getNome(), operacao, taxaCalculada
            ));
        }

        if (taxaCalculada.compareTo(BigDecimal.ONE) > 0) {
            return ResolucaoTaxaResult.impedido(String.format(
                    "Impedimento de cálculo: a regra ID %d ('%s') com ajuste %s resultou em taxa percentual superior a 100%% (%s).",
                    regra.getId(), regra.getNome(), operacao, taxaCalculada
            ));
        }

        String sinalOperacao = switch (operacao) {
            case ACRESCIMO_PONTOS -> "+";
            case DESCONTO_PONTOS -> "-";
            case MULTIPLICADOR_BASE -> "*";
            case DIVISOR_BASE -> "/";
            default -> "=";
        };

        String versaoRegra = String.format("REGRA#%d - %s [%s: Base %s %s %s = %s]",
                regra.getId(), regra.getNome(), operacao, taxaBase,
                sinalOperacao, valorAjuste, taxaCalculada);

        return sucessoPorRegra(regra, taxaCalculada, versaoRegra);
    }

    /**
     * Consulta a taxa padrão em tb_basecomiss delegando exclusivamente para CoberturaBaseService.
     * Regra adotada: reference_month estritamente igual à competência da venda.
     */
    private Optional<BigDecimal> buscarTaxaBase(Brand brand, Position position, LocalDate saleDate) {
        return buscarTaxaBase(brand, position, saleDate, null);
    }

    private Optional<BigDecimal> buscarTaxaBase(Brand brand, Position position, LocalDate saleDate, ContextoCompetencia contexto) {
        if (brand == null || position == null || saleDate == null) {
            return Optional.empty();
        }

        LocalDate mesRef = saleDate.withDayOfMonth(1);
        if (contexto != null && contexto.basesComissao() != null) {
            Optional<BaseComiss> emMemoria = buscarBaseComissEmMemoria(contexto.basesComissao(), brand, position, mesRef);
            if (emMemoria.isPresent()) {
                return emMemoria.map(BaseComiss::getPercentage);
            }
        }

        if (brand.getCode() == null || position.getCode() == null) {
            return Optional.empty();
        }
        YearMonth ym = YearMonth.from(saleDate);
        if (coberturaBaseService != null) {
            return coberturaBaseService.obterTaxaBase(brand.getCode(), position.getCode(), ym);
        }
        return Optional.empty();
    }

    private boolean regraAplica(Regra r, LocalDate saleDate, Integer codMarca, Integer codLoja, Integer codCargo, String matricula, String canal) {
        if (r.getStatus() != StatusRegra.ATIVA || r.getRemovidoEm() != null) {
            return false;
        }
        if (r.getDataInicio().isAfter(saleDate) || r.getDataFim().isBefore(saleDate)) {
            return false;
        }
        if (r.getCodMarca() != null && !r.getCodMarca().equals(codMarca)) {
            return false;
        }
        if (r.getCodLoja() != null && !r.getCodLoja().equals(codLoja)) {
            return false;
        }
        if (r.getCodCargo() != null && !r.getCodCargo().equals(codCargo)) {
            return false;
        }
        if (r.getMatricula() != null && !r.getMatricula().equals(matricula)) {
            return false;
        }
        if (r.getCanal() != null && (canal == null || !r.getCanal().equalsIgnoreCase(canal))) {
            return false;
        }
        return true;
    }

    private Optional<BaseComiss> buscarBaseComissEmMemoria(List<BaseComiss> bases, Brand brand, Position position, LocalDate mesRef) {
        if (bases == null || bases.isEmpty()) {
            return Optional.empty();
        }
        if (brand.getId() != null && position.getId() != null) {
            Optional<BaseComiss> opt = bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getId().equals(b.getBrand().getId())
                            && b.getPosition() != null && position.getId().equals(b.getPosition().getId())
                            && mesRef.equals(b.getReferenceMonth()))
                    .findFirst();
            if (opt.isPresent()) {
                return opt;
            }
            opt = bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getId().equals(b.getBrand().getId())
                            && b.getPosition() != null && position.getId().equals(b.getPosition().getId()))
                    .findFirst();
            if (opt.isPresent()) {
                return opt;
            }
        }
        if (brand.getCode() != null && position.getCode() != null) {
            Optional<BaseComiss> opt = bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getCode().equals(b.getBrand().getCode())
                            && b.getPosition() != null && position.getCode().equals(b.getPosition().getCode())
                            && mesRef.equals(b.getReferenceMonth()))
                    .findFirst();
            if (opt.isPresent()) {
                return opt;
            }
            return bases.stream()
                    .filter(b -> b.getBrand() != null && brand.getCode().equals(b.getBrand().getCode())
                            && b.getPosition() != null && position.getCode().equals(b.getPosition().getCode()))
                    .findFirst();
        }
        return Optional.empty();
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
        return especificidade(regra.getCodMarca(), regra.getCodLoja(), regra.getCodCargo(),
                regra.getMatricula(), regra.getCanal());
    }

    /** Peso de especificidade por dimensão informada (fonte única, reutilizada nas consultas da IA). */
    public static int especificidade(Integer codMarca, Integer codLoja, Integer codCargo, String matricula, String canal) {
        int peso = 0;
        if (matricula != null && !matricula.isBlank()) {
            peso += 16;
        }
        if (codLoja != null) {
            peso += 8;
        }
        if (codCargo != null) {
            peso += 4;
        }
        if (canal != null && !canal.isBlank() && !"PADRAO".equalsIgnoreCase(canal)) {
            peso += 2;
        }
        if (codMarca != null) {
            peso += 1;
        }
        return peso;
    }

    /** Sucesso por regra de negócio, levando o bônus da regra vencedora (nunca somado entre regras). */
    private ResolucaoTaxaResult sucessoPorRegra(Regra regra, BigDecimal taxa, String versaoRegra) {
        return ResolucaoTaxaResult.sucesso(taxa, regra.getId(), "REGRA_NEGOCIO", versaoRegra, null,
                regra.getBonusPercentual(), regra.getBonusValorFixo());
    }

    /** Exclui o registro técnico usado como referência para a taxa base. */
    private boolean isRegraPadraoTecnica(Regra regra) {
        return regra != null
                && REGRA_BASE_ID.equals(regra.getId())
                && regra.getCampanha() == null;
    }
}

