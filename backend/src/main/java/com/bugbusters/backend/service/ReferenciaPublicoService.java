package com.bugbusters.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.brand.Brand;
import com.bugbusters.backend.dto.campanha.RegraItemRequest;
import com.bugbusters.backend.dto.interpretador.proposta.TipoOperacaoBase;
import com.bugbusters.backend.dto.referencia.*;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.Campanha;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.position.Position;
import com.bugbusters.backend.registration.Registration;
import com.bugbusters.backend.repository.CampanhaRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;

@Service
public class ReferenciaPublicoService {

    private static final Logger log = LoggerFactory.getLogger(ReferenciaPublicoService.class);

    private final SaleRepository saleRepository;
    private final BaseComissRepository baseComissRepository;
    private final CampanhaRepository campanhaRepository;
    private final com.bugbusters.backend.repository.RegraRepository regraRepository;

    public ReferenciaPublicoService(
            SaleRepository saleRepository,
            BaseComissRepository baseComissRepository,
            CampanhaRepository campanhaRepository,
            com.bugbusters.backend.repository.RegraRepository regraRepository) {
        this.saleRepository = saleRepository;
        this.baseComissRepository = baseComissRepository;
        this.campanhaRepository = campanhaRepository;
        this.regraRepository = regraRepository;
    }

    /**
     * Lista todas as competências existentes no banco de dados com métricas de vendas
     * e indicadores de integridade e cobertura da base para seleção de referência.
     */
    @Transactional(readOnly = true)
    public List<CompetenciaReferenciaDTO> listarCompetenciasReferencia() {
        Set<String> todasCompetencias = new TreeSet<>(Comparator.reverseOrder());

        // 1. Coleta competências com transações de vendas
        List<Object[]> anosMesesVendas = saleRepository.findDistinctAnosEMesesVendas();
        for (Object[] row : anosMesesVendas) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                int ano = ((Number) row[0]).intValue();
                int mes = ((Number) row[1]).intValue();
                todasCompetencias.add(String.format("%04d-%02d", ano, mes));
            }
        }

        // 2. Coleta competências com tabelas de comissão base contratual
        List<Object[]> anosMesesBase = baseComissRepository.findDistinctAnosEMesesBaseComiss();
        for (Object[] row : anosMesesBase) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                int ano = ((Number) row[0]).intValue();
                int mes = ((Number) row[1]).intValue();
                todasCompetencias.add(String.format("%04d-%02d", ano, mes));
            }
        }

        List<CompetenciaReferenciaDTO> resultado = new ArrayList<>();
        for (String comp : todasCompetencias) {
            resultado.add(calcularMetricasCompetencia(comp));
        }

        return resultado;
    }

    /**
     * Consulta detalhada de uma competência específica para seleção de referência.
     */
    @Transactional(readOnly = true)
    public CompetenciaReferenciaDTO detalharCompetencia(String competenciaStr) {
        String compNormalizada = normalizarCompetencia(competenciaStr);
        return calcularMetricasCompetencia(compNormalizada);
    }

    /**
     * Executa a consulta reutilizável de recorte da união dos públicos de uma coleção de regras
     * em uma competência de referência.
     *
     * Regras fundamentais:
     * - Filtros de cada regra combinados por E (marca, loja, cargo, canal e matrícula).
     * - Coleção de regras combinada por OU (união dos públicos).
     * - Deduplicação estrita pelo ID da venda (uma única ocorrência por venda).
     * - Recorte por público ANTES das faixas de valor (preserva vendas do público e classifica faixa).
     * - Validação completa de vínculos (RH, cargo, loja, marca), valores (> 0) e presença de taxa base em tb_basecomiss.
     * - Diagnóstico de sobreposição com pesos de especificidade e alerta de conflito/empate.
     */
    @Transactional(readOnly = true)
    public RecortePublicoResponse recortarUniaoPublicos(RecortePublicoRequest request) {
        if (request == null) {
            throw new BusinessException("A requisição de recorte de público não pode ser nula.");
        }

        String competenciaStr = normalizarCompetencia(request.competencia());
        YearMonth ym = YearMonth.parse(competenciaStr);
        LocalDate inicioMes = ym.atDay(1);
        LocalDate fimMes = ym.atEndOfMonth();

        // 1. Carrega todas as vendas do mês de referência com seus relacionamentos essenciais
        List<Sale> vendasDoMes = saleRepository.findVendasPorPeriodoComRelacionamentos(inicioMes, fimMes);
        int totalVendasCompetencia = vendasDoMes.size();
        BigDecimal valorTotalCompetencia = vendasDoMes.stream()
                .map(s -> s.getValue() != null ? s.getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 2. Se a competência não possui nenhuma venda registrada: impedimento específico de referência
        if (vendasDoMes.isEmpty()) {
            return new RecortePublicoResponse(
                    competenciaStr,
                    0,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    0,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    0,
                    0,
                    0,
                    0,
                    false,
                    List.of(String.format("Referência insuficiente: a competência '%s' não possui nenhuma transação de venda registrada.", competenciaStr)),
                    List.of(),
                    0,
                    List.of()
            );
        }

        // 3. Resolve as regras a serem avaliadas (de campanha existente ou do payload)
        List<RegraItemRequest> regrasAvaliadas = resolverRegras(request);
        if (regrasAvaliadas.isEmpty()) {
            throw new BusinessException("Nenhuma regra informada ou encontrada para a realização do recorte de público.");
        }

        // 4. Executa o recorte da união de públicos (OU entre regras, E nos filtros de cada regra)
        List<VendaRecortadaDTO> todasVendasRecortadas = new ArrayList<>();
        Map<String, Set<UUID>> mapaSobreposicoes = new LinkedHashMap<>();
        int countVendasAtendemFaixa = 0;
        int countVendasComImpedimento = 0;
        int countVendasAptas = 0;
        int countVendasSobrepostas = 0;
        boolean possuiAmbiguidadeCritica = false;
        BigDecimal valorTotalRecortado = BigDecimal.ZERO;

        for (Sale sale : vendasDoMes) {
            List<RegraItemRequest> regrasQueCobrem = new ArrayList<>();

            for (RegraItemRequest regra : regrasAvaliadas) {
                if (atendePublico(regra, sale)) {
                    regrasQueCobrem.add(regra);
                }
            }

            // Se atende a pelo menos uma regra (OU), a venda entra no recorte (deduplicada por ID)
            if (!regrasQueCobrem.isEmpty()) {
                VendaRecortadaDTO vendaDTO = processarVendaRecortada(sale, regrasQueCobrem, competenciaStr);
                todasVendasRecortadas.add(vendaDTO);

                valorTotalRecortado = valorTotalRecortado.add(sale.getValue() != null ? sale.getValue() : BigDecimal.ZERO);

                if (vendaDTO.atendeFaixaValor()) {
                    countVendasAtendemFaixa++;
                }
                if (vendaDTO.valida()) {
                    countVendasAptas++;
                } else {
                    countVendasComImpedimento++;
                }
                if (vendaDTO.temSobreposicao()) {
                    countVendasSobrepostas++;
                    // Agrupa par/conjunto de regras sobrepostas
                    String chaveSobreposicao = regrasQueCobrem.stream()
                            .map(r -> r.nome() != null ? r.nome() : "Regra#" + r.blocoId())
                            .sorted()
                            .collect(Collectors.joining(" + "));
                    mapaSobreposicoes.computeIfAbsent(chaveSobreposicao, k -> new HashSet<>()).add(sale.getId());
                }
                if (vendaDTO.temEmpateEspecificidade()) {
                    possuiAmbiguidadeCritica = true;
                }
            }
        }

        valorTotalRecortado = valorTotalRecortado.setScale(2, RoundingMode.HALF_UP);
        int totalRecortadas = todasVendasRecortadas.size();

        // 5. Percentuais executivos de cobertura
        BigDecimal percCoberturaVendas = totalVendasCompetencia > 0
                ? BigDecimal.valueOf(totalRecortadas)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalVendasCompetencia), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal percCoberturaValor = valorTotalCompetencia.compareTo(BigDecimal.ZERO) > 0
                ? valorTotalRecortado
                        .multiply(BigDecimal.valueOf(100))
                        .divide(valorTotalCompetencia, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 6. Diagnósticos consolidados de sobreposição
        List<SobreposicaoRegraDTO> diagnosticoSobreposicoes = montarDiagnosticosSobreposicao(mapaSobreposicoes, regrasAvaliadas);

        // 7. Filtros opcionais de listagem e paginação para o payload de retorno
        List<VendaRecortadaDTO> vendasFiltradas = todasVendasRecortadas.stream()
                .filter(v -> !Boolean.TRUE.equals(request.apenasSobrepostas()) || v.temSobreposicao())
                .filter(v -> !Boolean.TRUE.equals(request.apenasComImpedimento()) || !v.valida())
                .toList();

        int pagina = request.pagina() != null ? request.pagina() : 0;
        int limite = request.limite() != null ? request.limite() : 500;
        int fromIndex = Math.min(pagina * limite, vendasFiltradas.size());
        int toIndex = Math.min(fromIndex + limite, vendasFiltradas.size());
        List<VendaRecortadaDTO> vendasPaginadas = vendasFiltradas.subList(fromIndex, toIndex);

        List<String> impedimentosGerais = new ArrayList<>();
        if (totalRecortadas == 0) {
            impedimentosGerais.add(String.format("Nenhuma venda da competência '%s' pertence ao público-alvo das regras avaliadas.", competenciaStr));
        }
        if (possuiAmbiguidadeCritica) {
            impedimentosGerais.add("Existem vendas com empate na maior especificidade entre regras ativas. O cálculo dessas vendas será impedido por ambiguidade.");
        }

        return new RecortePublicoResponse(
                competenciaStr,
                totalVendasCompetencia,
                valorTotalCompetencia,
                totalRecortadas,
                valorTotalRecortado,
                percCoberturaVendas,
                percCoberturaValor,
                countVendasAptas,
                countVendasComImpedimento,
                countVendasAtendemFaixa,
                countVendasSobrepostas,
                possuiAmbiguidadeCritica,
                impedimentosGerais,
                diagnosticoSobreposicoes,
                vendasPaginadas.size(),
                vendasPaginadas
        );
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE RECORTE, ESPECIFICIDADE E VALIDAÇÃO
    // =========================================================================

    /**
     * Verifica se uma venda atende aos filtros de público-alvo de uma regra (combinados por E).
     * Importante: NÃO avalia faixas de valor aqui, pois o recorte de público é prévio ao valor.
     */
    public boolean atendePublico(RegraItemRequest regra, Sale sale) {
        if (regra == null || sale == null) {
            return false;
        }

        // 1. Canal: se especificado e não "PADRAO", deve ser idêntico
        if (regra.canal() != null && !regra.canal().isBlank() && !"PADRAO".equalsIgnoreCase(regra.canal())) {
            if (sale.getSaleChannel() == null || !sale.getSaleChannel().equalsIgnoreCase(regra.canal().trim())) {
                return false;
            }
        }

        // 2. Marca: se especificada, deve coincidir com a marca da venda
        if (regra.codMarca() != null) {
            if (sale.getBrand() == null || !Objects.equals(sale.getBrand().getCode(), regra.codMarca())) {
                return false;
            }
        }

        // 3. Loja: se especificada, deve coincidir com a loja da venda
        if (regra.codLoja() != null) {
            if (sale.getStore() == null || !Objects.equals(sale.getStore().getCode(), regra.codLoja())) {
                return false;
            }
        }

        // 4. Cargo: se especificado, deve coincidir com o cargo do colaborador da venda
        if (regra.codCargo() != null) {
            if (sale.getRegistration() == null
                    || sale.getRegistration().getPosition() == null
                    || !Objects.equals(sale.getRegistration().getPosition().getCode(), regra.codCargo())) {
                return false;
            }
        }

        // 5. Matrícula: se especificada, deve coincidir com a matrícula individual
        if (regra.matricula() != null && !regra.matricula().isBlank()) {
            if (sale.getRegistration() == null
                    || sale.getRegistration().getRegistration() == null
                    || !sale.getRegistration().getRegistration().equalsIgnoreCase(regra.matricula().trim())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Valida se o valor monetário da venda atende aos limites da regra (faixa inclusiva/exclusiva).
     */
    public boolean atendeFaixaValor(RegraItemRequest regra, BigDecimal valorVenda) {
        if (regra == null || valorVenda == null) {
            return false;
        }

        if (regra.valorMinimo() != null) {
            boolean minInclusivo = regra.minInclusivo() == null || regra.minInclusivo();
            int compMin = valorVenda.compareTo(regra.valorMinimo());
            if (minInclusivo ? compMin < 0 : compMin <= 0) {
                return false;
            }
        }

        if (regra.valorMaximo() != null) {
            boolean maxInclusivo = regra.maxInclusivo() == null || regra.maxInclusivo();
            int compMax = valorVenda.compareTo(regra.valorMaximo());
            if (maxInclusivo ? compMax > 0 : compMax >= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Calcula o peso determinístico de especificidade da regra conforme a matriz contratual:
     * - Matrícula (individual): 16
     * - Loja: 8
     * - Cargo: 4
     * - Canal específico (!= PADRAO): 2
     * - Marca: 1
     */
    public int calcularEspecificidade(RegraItemRequest regra) {
        if (regra == null) return 0;
        int peso = 0;
        if (regra.matricula() != null && !regra.matricula().isBlank()) {
            peso += 16;
        }
        if (regra.codLoja() != null) {
            peso += 8;
        }
        if (regra.codCargo() != null) {
            peso += 4;
        }
        if (regra.canal() != null && !regra.canal().isBlank() && !"PADRAO".equalsIgnoreCase(regra.canal())) {
            peso += 2;
        }
        if (regra.codMarca() != null) {
            peso += 1;
        }
        return peso;
    }

    private VendaRecortadaDTO processarVendaRecortada(Sale sale, List<RegraItemRequest> regrasQueCobrem, String competenciaStr) {
        List<String> impedimentos = new ArrayList<>();
        List<RegraAplicavelVendaDTO> regrasDTO = new ArrayList<>();

        Registration reg = sale.getRegistration();
        Position pos = (reg != null) ? reg.getPosition() : null;
        Brand brand = sale.getBrand();

        // 1. Validação de Vínculos Cadastrais
        if (reg == null || reg.getRegistration() == null || reg.getRegistration().isBlank()) {
            impedimentos.add("Colaborador não identificado ou sem vínculo com o RH");
        }
        if (pos == null) {
            impedimentos.add("Colaborador sem cargo cadastrado no RH");
        }
        if (sale.getStore() == null || sale.getStore().getCode() == null) {
            impedimentos.add("Venda sem loja vinculada");
        }
        if (brand == null || brand.getCode() == null) {
            impedimentos.add("Venda sem marca vinculada");
        }

        // 2. Validação de Valores
        if (sale.getValue() == null || sale.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            impedimentos.add("Valor da venda inválido: deve ser estritamente positivo");
        }

        // 3. Validação de Vigência Contratual
        if (reg != null && sale.getSaleDate() != null) {
            if (reg.getAdmissDate() != null && sale.getSaleDate().isBefore(reg.getAdmissDate())) {
                impedimentos.add(String.format("Data da venda (%s) é anterior à admissão do colaborador (%s)",
                        sale.getSaleDate(), reg.getAdmissDate()));
            }
            if (reg.getDemissDate() != null && sale.getSaleDate().isAfter(reg.getDemissDate())) {
                impedimentos.add(String.format("Data da venda (%s) é posterior à demissão do colaborador (%s)",
                        sale.getSaleDate(), reg.getDemissDate()));
            }
        }

        // 4. Validação de Taxa Base Contratual (tb_basecomiss)
        Optional<BigDecimal> baseRateOpt = Optional.empty();
        if (brand != null && pos != null) {
            baseRateOpt = baseComissRepository
                    .findFirstByBrandCodeAndPositionCodeOrderByReferenceMonthDesc(brand.getCode(), pos.getCode())
                    .map(BaseComiss::getPercentage);
            if (baseRateOpt.isEmpty()) {
                impedimentos.add(String.format("Taxa base contratual ausente em tb_basecomiss para Marca '%s' e Cargo '%s'",
                        brand.getDescription() != null ? brand.getDescription() : brand.getCode(),
                        pos.getDescription() != null ? pos.getDescription() : pos.getCode()));
            }
        }

        // 5. Avaliação de Regras Aplicáveis e Faixas de Valor
        boolean atendeFaixaPeloMenosUma = false;
        int maiorEspecificidade = -1;
        List<RegraItemRequest> regrasEmpatadas = new ArrayList<>();
        Long idRegraVencedora = null;
        String nomeRegraVencedora = null;

        for (int i = 0; i < regrasQueCobrem.size(); i++) {
            RegraItemRequest r = regrasQueCobrem.get(i);
            boolean atendeFaixa = atendeFaixaValor(r, sale.getValue());
            if (atendeFaixa) {
                atendeFaixaPeloMenosUma = true;
            }

            int esp = calcularEspecificidade(r);
            Long idRegra = (long) (i + 1);

            // Valida operação de ajuste sobre a base se aplicável
            if (r.tipoOperacao() != null && r.tipoOperacao() != TipoOperacaoBase.DEFINIR_TAXA) {
                if (baseRateOpt.isEmpty()) {
                    impedimentos.add(String.format("A regra '%s' requer taxa base contratual para operação '%s', mas a base não está disponível.",
                            r.nome(), r.tipoOperacao()));
                } else if (r.valorAjuste() == null) {
                    impedimentos.add(String.format("A regra '%s' com operação '%s' não possui valor de ajuste definido.",
                            r.nome(), r.tipoOperacao()));
                }
            }

            regrasDTO.add(new RegraAplicavelVendaDTO(
                    idRegra,
                    r.blocoId(),
                    r.nome(),
                    esp,
                    atendeFaixa,
                    r.taxa(),
                    r.tipoOperacao() != null ? r.tipoOperacao() : TipoOperacaoBase.DEFINIR_TAXA,
                    r.valorAjuste()
            ));

            if (esp > maiorEspecificidade) {
                maiorEspecificidade = esp;
                regrasEmpatadas.clear();
                regrasEmpatadas.add(r);
                idRegraVencedora = idRegra;
                nomeRegraVencedora = r.nome();
            } else if (esp == maiorEspecificidade) {
                regrasEmpatadas.add(r);
            }
        }

        boolean temSobreposicao = regrasQueCobrem.size() > 1;
        boolean temEmpateEspecificidade = temSobreposicao && regrasEmpatadas.size() > 1;

        if (temEmpateEspecificidade) {
            String nomesEmpatados = regrasEmpatadas.stream()
                    .map(r -> r.nome() != null ? r.nome() : "Regra#" + r.blocoId())
                    .collect(Collectors.joining(", "));
            impedimentos.add(String.format("Conflito de regras: múltiplas regras empatadas na maior especificidade (peso %d): [%s]",
                    maiorEspecificidade, nomesEmpatados));
            idRegraVencedora = null;
            nomeRegraVencedora = null;
        }

        boolean valida = impedimentos.isEmpty();

        return new VendaRecortadaDTO(
                sale.getId(),
                reg != null ? reg.getRegistration() : null,
                pos != null ? pos.getCode() : null,
                pos != null ? pos.getDescription() : null,
                sale.getStore() != null ? sale.getStore().getCode() : null,
                brand != null ? brand.getCode() : null,
                brand != null ? brand.getDescription() : null,
                sale.getSaleChannel() != null ? sale.getSaleChannel() : "PADRAO",
                sale.getSaleDate(),
                sale.getValue(),
                regrasDTO,
                idRegraVencedora,
                nomeRegraVencedora,
                atendeFaixaPeloMenosUma,
                temSobreposicao,
                temEmpateEspecificidade,
                valida,
                impedimentos
        );
    }

    private List<SobreposicaoRegraDTO> montarDiagnosticosSobreposicao(
            Map<String, Set<UUID>> mapaSobreposicoes,
            List<RegraItemRequest> regrasAvaliadas) {

        List<SobreposicaoRegraDTO> diagnosticos = new ArrayList<>();
        Map<String, RegraItemRequest> mapaRegrasPorNome = regrasAvaliadas.stream()
                .collect(Collectors.toMap(
                        r -> r.nome() != null ? r.nome() : "Regra#" + r.blocoId(),
                        r -> r,
                        (existente, substituto) -> existente
                ));

        for (Map.Entry<String, Set<UUID>> entry : mapaSobreposicoes.entrySet()) {
            String chave = entry.getKey();
            int totalAfetadas = entry.getValue().size();
            List<String> nomes = Arrays.stream(chave.split(" \\+ ")).map(String::trim).toList();

            int maxEsp = -1;
            int countMax = 0;
            for (String nome : nomes) {
                RegraItemRequest r = mapaRegrasPorNome.get(nome);
                if (r != null) {
                    int esp = calcularEspecificidade(r);
                    if (esp > maxEsp) {
                        maxEsp = esp;
                        countMax = 1;
                    } else if (esp == maxEsp) {
                        countMax++;
                    }
                }
            }

            boolean temEmpate = countMax > 1;
            String tipo = temEmpate ? "EMPATE" : "SOBREPOSTA";
            String mensagem = temEmpate
                    ? String.format("As regras sobrepostas possuem o mesmo nível de prioridade (peso %d). Em vendas comuns, haverá bloqueio por ambiguidade.", maxEsp)
                    : String.format("A sobreposição possui regra de maior especificidade que terá precedência determinística.", maxEsp);

            diagnosticos.add(new SobreposicaoRegraDTO(
                    nomes,
                    totalAfetadas,
                    tipo,
                    temEmpate,
                    mensagem
            ));
        }

        return diagnosticos;
    }

    public List<RegraItemRequest> resolverRegras(RecortePublicoRequest request) {
        if (request.regras() != null && !request.regras().isEmpty()) {
            return request.regras();
        }

        if (request.campanhaId() != null) {
            Campanha campanha = campanhaRepository.findById(request.campanhaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Campanha não encontrada com ID: " + request.campanhaId()));

            List<Regra> regras = (regraRepository != null)
                    ? regraRepository.findAllByCampanhaIdAndRemovidoEmIsNullOrderByIdAsc(campanha.getId())
                    : List.of();

            if (regras.isEmpty() && campanha.getRegras() != null) {
                regras = campanha.getRegras().stream()
                        .filter(r -> r.getRemovidoEm() == null)
                        .toList();
            }

            if (regras.isEmpty()) {
                throw new BusinessException(String.format("A campanha ID %d ('%s') não possui regras vinculadas.",
                        campanha.getId(), campanha.getTitulo()));
            }

            return regras.stream()
                    .map(this::toItemRequest)
                    .toList();
        }

        throw new BusinessException("Informe a coleção 'regras' ou um 'campanhaId' válido para executar o recorte de público.");
    }

    public RegraItemRequest toItemRequest(Regra r) {
        return new RegraItemRequest(
                "regra-" + r.getId(),
                r.getNome(),
                r.getNome(),
                r.getCanal(),
                r.getCodMarca(),
                r.getDescrMarca(),
                r.getCodLoja(),
                r.getCodCargo(),
                r.getDescriCargo(),
                r.getMatricula(),
                r.getValorMinimo(),
                r.getMinInclusivo(),
                r.getValorMaximo(),
                r.getMaxInclusivo(),
                r.getTipoOperacao() != null ? r.getTipoOperacao() : TipoOperacaoBase.DEFINIR_TAXA,
                r.getValorAjuste(),
                null,
                null,
                null,
                r.getTaxa(),
                null,
                null,
                null,
                true,
                r.getStatus() != null ? r.getStatus() : StatusRegra.ATIVA,
                r.getBonusPercentual(),
                r.getBonusValorFixo()
        );
    }

    private CompetenciaReferenciaDTO calcularMetricasCompetencia(String compNormalizada) {
        YearMonth ym = YearMonth.parse(compNormalizada);
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        List<Sale> vendas = saleRepository.findVendasPorPeriodoComRelacionamentos(inicio, fim);
        int totalVendas = vendas.size();
        BigDecimal valorTotal = vendas.stream()
                .map(s -> s.getValue() != null ? s.getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate minData = null;
        LocalDate maxData = null;
        int comVinculoValido = 0;
        int comImpedimento = 0;

        for (Sale s : vendas) {
            LocalDate dt = s.getSaleDate();
            if (minData == null || (dt != null && dt.isBefore(minData))) minData = dt;
            if (maxData == null || (dt != null && dt.isAfter(maxData))) maxData = dt;

            Registration reg = s.getRegistration();
            boolean vinculoOk = reg != null
                    && reg.getPosition() != null
                    && s.getStore() != null
                    && s.getBrand() != null
                    && s.getValue() != null
                    && s.getValue().compareTo(BigDecimal.ZERO) > 0
                    && (reg.getAdmissDate() == null || !s.getSaleDate().isBefore(reg.getAdmissDate()))
                    && (reg.getDemissDate() == null || !s.getSaleDate().isAfter(reg.getDemissDate()));

            if (vinculoOk) {
                comVinculoValido++;
            } else {
                comImpedimento++;
            }
        }

        long countBase = baseComissRepository.countByReferenceMonthBetween(inicio, fim);
        boolean possuiBaseComiss = countBase > 0;

        // Se não tiver taxa base no mês exato, verifica se existe taxa base cadastrada no sistema (fallback disponível)
        if (!possuiBaseComiss) {
            long totalGeralBase = baseComissRepository.count();
            possuiBaseComiss = totalGeralBase > 0;
        }

        String status;
        List<String> mensagens = new ArrayList<>();

        if (totalVendas == 0) {
            status = "SEM_VENDAS";
            mensagens.add("Nenhuma transação de venda registrada para esta competência.");
        } else if (!possuiBaseComiss) {
            status = "INSUFICIENTE";
            mensagens.add("Competência sem taxas base cadastradas em tb_basecomiss para fallback de comissão.");
        } else if (comImpedimento > 0) {
            status = "PARCIAL";
            mensagens.add(String.format("%d de %d vendas possuem inconsistências cadastrais ou vigência inválida.", comImpedimento, totalVendas));
        } else {
            status = "DISPONIVEL";
            mensagens.add("Competência com cobertura cadastral completa e apta para seleção de referência.");
        }

        return new CompetenciaReferenciaDTO(
                compNormalizada,
                totalVendas,
                valorTotal,
                minData,
                maxData,
                comVinculoValido,
                comImpedimento,
                possuiBaseComiss,
                (int) countBase,
                status,
                mensagens
        );
    }

    private String normalizarCompetencia(String comp) {
        if (comp == null || comp.isBlank()) {
            throw new BusinessException("A competência é obrigatória para a seleção de referência.");
        }
        String limpo = comp.trim();
        if (limpo.length() >= 7) {
            return limpo.substring(0, 7);
        }
        throw new BusinessException(String.format("Formato de competência inválido: '%s'. Utilize YYYY-MM.", comp));
    }
}
