package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.relatorio.*;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.exception.ResourceNotFoundException;
import com.bugbusters.backend.model.CampanhaApuracao;
import com.bugbusters.backend.model.FechamentoCompetencia;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.model.StatusFechamento;
import com.bugbusters.backend.model.TipoAcaoAuditoria;
import com.bugbusters.backend.model.TipoEntidadeAuditoria;
import com.bugbusters.backend.repository.CampanhaApuracaoRepository;
import com.bugbusters.backend.repository.FechamentoCompetenciaRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço responsável por consolidar e exportar o relatório de fechamento
 * contábil e apuração de comissões por canal e equipe (BUG-110 / BACK-15).
 */
@Service
public class RelatorioFechamentoService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioFechamentoService.class);
    private static final Locale LOCALE_PT_BR = Locale.forLanguageTag("pt-BR");

    private final FechamentoCompetenciaRepository fechamentoCompetenciaRepository;
    private final CampanhaApuracaoRepository apuracaoRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final SaleRepository saleRepository;
    private final AuditoriaGeralService auditoriaService;

    @Autowired
    public RelatorioFechamentoService(
            FechamentoCompetenciaRepository fechamentoCompetenciaRepository,
            CampanhaApuracaoRepository apuracaoRepository,
            ResultadoCalculoRepository resultadoCalculoRepository,
            SaleRepository saleRepository,
            @Autowired(required = false) AuditoriaGeralService auditoriaService
    ) {
        this.fechamentoCompetenciaRepository = fechamentoCompetenciaRepository;
        this.apuracaoRepository = apuracaoRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.saleRepository = saleRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Gera o relatório consolidado de fechamento para uma competência oficial (ex: '2026-10').
     */
    @Transactional(readOnly = true)
    public RelatorioFechamentoConsolidadoDTO gerarRelatorioCompetencia(String competenciaStr) {
        String competencia = normalizarCompetencia(competenciaStr);

        FechamentoCompetencia fechamento = fechamentoCompetenciaRepository
                .findFirstByCompetenciaAndStatusOrderByConcluidoEmDesc(competencia, StatusFechamento.CONCLUIDO)
                .or(() -> fechamentoCompetenciaRepository.findFirstByCompetenciaOrderByIniciadoEmDesc(competencia))
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum fechamento registrado para a competência: " + competencia));

        List<ResultadoCalculo> resultados = resultadoCalculoRepository.findAllByFechamentoIdOrderByDataVendaAsc(fechamento.getId());

        YearMonth ym = YearMonth.parse(competencia);
        LocalDate inicioMes = ym.atDay(1);
        LocalDate fimMes = ym.atEndOfMonth();

        return consolidarDados(
                "Competência " + competencia,
                fechamento.getProtocolo(),
                fechamento.getStatus().name(),
                competencia,
                inicioMes,
                fimMes,
                fechamento.getExecutadoPor(),
                fechamento.getConcluidoEm(),
                fechamento.getTotalVendasProcessadas(),
                fechamento.getTotalImpedimentos(),
                resultados,
                inicioMes,
                fimMes
        );
    }

    /**
     * Gera o relatório consolidado de fechamento a partir do protocolo oficial.
     */
    @Transactional(readOnly = true)
    public RelatorioFechamentoConsolidadoDTO gerarRelatorioPorProtocolo(UUID protocolo) {
        FechamentoCompetencia fechamento = fechamentoCompetenciaRepository.findByProtocolo(protocolo)
                .orElseThrow(() -> new ResourceNotFoundException("Fechamento não encontrado com o protocolo: " + protocolo));

        return gerarRelatorioCompetencia(fechamento.getCompetencia());
    }

    /**
     * Gera o relatório consolidado da apuração definitiva de uma campanha.
     */
    @Transactional(readOnly = true)
    public RelatorioFechamentoConsolidadoDTO gerarRelatorioCampanha(Long campanhaId, UUID apuracaoId) {
        CampanhaApuracao apuracao;
        if (apuracaoId != null) {
            apuracao = apuracaoRepository.findById(apuracaoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Apuração não encontrada com ID: " + apuracaoId));
        } else {
            apuracao = apuracaoRepository.findFirstByCampanhaIdOrderByExecutadoEmDesc(campanhaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Nenhuma apuração definitiva encontrada para a campanha ID: " + campanhaId));
        }

        if (apuracao.getCampanha() != null && !campanhaId.equals(apuracao.getCampanha().getId())) {
            throw new BusinessException("A apuração não pertence à campanha " + campanhaId);
        }

        List<ResultadoCalculo> resultados = resultadoCalculoRepository.findAllByApuracaoCampanhaIdOrderByDataVendaAsc(apuracao.getId());

        String tituloCampanha = apuracao.getCampanha() != null ? apuracao.getCampanha().getTitulo() : "Campanha #" + campanhaId;
        String identificador = String.format("%s (Campanha #%d - V%d)", tituloCampanha, campanhaId, apuracao.getVersaoCampanhaNumero());

        return consolidarDados(
                identificador,
                apuracao.getProtocolo(),
                apuracao.getStatus(),
                apuracao.getCompetencia(),
                apuracao.getDataInicio(),
                apuracao.getDataFim(),
                apuracao.getExecutadoPor(),
                apuracao.getExecutadoEm(),
                apuracao.getTotalVendasProcessadas(),
                apuracao.getTotalImpedimentos(),
                resultados,
                apuracao.getDataInicio(),
                apuracao.getDataFim()
        );
    }

    /**
     * Exporta o relatório consolidado em formato CSV com codificação UTF-8 com BOM e delimitador customizável.
     */
    public byte[] exportarCsv(RelatorioFechamentoConsolidadoDTO relatorio, TipoExportacaoRelatorio tipo, String delimitador) {
        if (relatorio == null) {
            throw new BusinessException("Dados do relatório não podem ser nulos para exportação.");
        }

        String delim = (delimitador != null && !delimitador.isEmpty()) ? delimitador : ";";
        TipoExportacaoRelatorio modo = tipo != null ? tipo : TipoExportacaoRelatorio.DETALHADO;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // Escreve UTF-8 BOM (\uFEFF) para compatibilidade perfeita com Microsoft Excel
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                switch (modo) {
                    case CONSOLIDADO_CANAL -> exportarSecaoCanal(writer, relatorio, delim);
                    case CONSOLIDADO_EQUIPE -> exportarSecaoEquipe(writer, relatorio, delim);
                    case CONSOLIDADO_COLABORADOR -> exportarSecaoColaborador(writer, relatorio, delim);
                    case COMPLETO -> exportarRelatorioCompleto(writer, relatorio, delim);
                    case DETALHADO -> exportarSecaoDetalhada(writer, relatorio, delim);
                }
                writer.flush();
            }

            if (auditoriaService != null) {
                String usuario = relatorio.resumo() != null && relatorio.resumo().executadoPor() != null
                        ? relatorio.resumo().executadoPor() : "SISTEMA";
                auditoriaService.registrarAcao(
                        usuario,
                        TipoAcaoAuditoria.EXPORTACAO_RELATORIO,
                        TipoEntidadeAuditoria.RELATORIO,
                        relatorio.resumo() != null && relatorio.resumo().protocolo() != null
                                ? relatorio.resumo().protocolo().toString() : "GERAL",
                        null,
                        String.format("Exportação de relatório CSV no modo %s", modo),
                        String.format("{\"modo\": \"%s\", \"delimitador\": \"%s\"}", modo, delim)
                );
            }

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Erro na geração de CSV de fechamento", e);
            throw new BusinessException("Falha ao exportar relatório CSV: " + e.getMessage());
        }
    }

    private RelatorioFechamentoConsolidadoDTO consolidarDados(
            String identificador,
            UUID protocolo,
            String status,
            String competencia,
            LocalDate dataInicio,
            LocalDate dataFim,
            String executadoPor,
            java.time.OffsetDateTime executadoEm,
            int totalVendasProcessadas,
            int totalImpedimentos,
            List<ResultadoCalculo> resultados,
            LocalDate inicioPeriodo,
            LocalDate fimPeriodo
    ) {
        // Carrega vendas com relacionamentos para enriquecer canais, nomes de lojas, marcas e cargos
        List<Sale> vendasPeriodo = (inicioPeriodo != null && fimPeriodo != null)
                ? saleRepository.findVendasPorPeriodoComRelacionamentos(inicioPeriodo, fimPeriodo)
                : List.of();

        Map<UUID, Sale> salesById = vendasPeriodo.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(Sale::getId, s -> s, (a, b) -> a));

        List<ItemRelatorioFechamentoDTO> itensDetalhados = new ArrayList<>();

        BigDecimal totalVendas = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalComissao = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBonusPerc = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBonusFixo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Set<String> colaboradoresDistintos = new HashSet<>();
        Set<Integer> lojasDistintas = new HashSet<>();
        Set<String> canaisDistintos = new HashSet<>();

        // Mapas para consolidação
        Map<String, CanalAccumulator> canalMap = new LinkedHashMap<>();
        Map<String, EquipeAccumulator> equipeMap = new LinkedHashMap<>();
        Map<String, ColaboradorAccumulator> colaboradorMap = new LinkedHashMap<>();

        for (ResultadoCalculo r : resultados) {
            Sale sale = r.getIdVenda() != null ? salesById.get(r.getIdVenda()) : null;

            String canal = (sale != null && sale.getSaleChannel() != null && !sale.getSaleChannel().isBlank())
                    ? sale.getSaleChannel()
                    : "PADRAO";

            String nomeLoja = (sale != null && sale.getStore() != null && sale.getStore().getDescription() != null)
                    ? sale.getStore().getDescription()
                    : (r.getCodLoja() != null ? "Loja " + r.getCodLoja() : "Loja Geral");

            String nomeMarca = (sale != null && sale.getBrand() != null && sale.getBrand().getDescription() != null)
                    ? sale.getBrand().getDescription()
                    : (r.getCodMarca() != null ? "Marca " + r.getCodMarca() : "Marca Geral");

            String nomeCargo = (sale != null && sale.getRegistration() != null && sale.getRegistration().getPosition() != null && sale.getRegistration().getPosition().getDescription() != null)
                    ? sale.getRegistration().getPosition().getDescription()
                    : (r.getCodCargo() != null ? "Cargo " + r.getCodCargo() : "Vendedor");

            BigDecimal valVenda = r.getValorVenda() != null ? r.getValorVenda() : BigDecimal.ZERO;
            BigDecimal valComissao = r.getValorComissao() != null ? r.getValorComissao() : BigDecimal.ZERO;
            BigDecimal valBonusPerc = r.getValorBonusPercentual() != null ? r.getValorBonusPercentual() : BigDecimal.ZERO;
            BigDecimal valBonusFixo = r.getValorBonusFixo() != null ? r.getValorBonusFixo() : BigDecimal.ZERO;
            BigDecimal valTotalBonus = valBonusPerc.add(valBonusFixo).setScale(2, RoundingMode.HALF_UP);

            totalVendas = totalVendas.add(valVenda);
            totalComissao = totalComissao.add(valComissao);
            totalBonusPerc = totalBonusPerc.add(valBonusPerc);
            totalBonusFixo = totalBonusFixo.add(valBonusFixo);

            if (r.getMatricula() != null) colaboradoresDistintos.add(r.getMatricula());
            if (r.getCodLoja() != null) lojasDistintas.add(r.getCodLoja());
            canaisDistintos.add(canal);

            ItemRelatorioFechamentoDTO item = new ItemRelatorioFechamentoDTO(
                    r.getIdVenda(),
                    r.getProtocoloCalculo(),
                    r.getDataVenda(),
                    r.getMatricula(),
                    r.getCodLoja(),
                    nomeLoja,
                    r.getCodMarca(),
                    nomeMarca,
                    r.getCodCargo(),
                    nomeCargo,
                    canal,
                    valVenda,
                    r.getTaxaAplicada(),
                    valComissao.subtract(valTotalBonus).max(BigDecimal.ZERO),
                    valBonusPerc,
                    valBonusFixo,
                    valComissao,
                    r.getRegraId(),
                    r.getOrigemTaxa(),
                    r.getVersaoCampanhaNumero()
            );
            itensDetalhados.add(item);

            // Acumula por Canal
            canalMap.computeIfAbsent(canal, k -> new CanalAccumulator(k))
                    .acumular(valVenda, valComissao, valTotalBonus, r.getMatricula());

            // Acumula por Equipe (Chave: codLoja + "#" + codMarca)
            String equipeKey = (r.getCodLoja() != null ? r.getCodLoja() : 0) + "#" + (r.getCodMarca() != null ? r.getCodMarca() : 0);
            equipeMap.computeIfAbsent(equipeKey, k -> new EquipeAccumulator(r.getCodLoja(), nomeLoja, r.getCodMarca(), nomeMarca))
                    .acumular(valVenda, valComissao, valTotalBonus, r.getMatricula());

            // Acumula por Colaborador
            String matKey = r.getMatricula() != null ? r.getMatricula() : "SEM_MATRICULA";
            colaboradorMap.computeIfAbsent(matKey, k -> new ColaboradorAccumulator(
                            k, r.getCodCargo(), nomeCargo, r.getCodLoja(), nomeLoja, r.getCodMarca(), nomeMarca))
                    .acumular(valVenda, valComissao, valBonusPerc, valBonusFixo);
        }

        BigDecimal taxaMediaEfetivaGeral = totalVendas.compareTo(BigDecimal.ZERO) > 0
                ? totalComissao.divide(totalVendas, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        BigDecimal totalBonusGeral = totalBonusPerc.add(totalBonusFixo).setScale(2, RoundingMode.HALF_UP);

        ResumoFechamentoDTO resumo = new ResumoFechamentoDTO(
                identificador,
                protocolo,
                status,
                competencia,
                dataInicio,
                dataFim,
                executadoPor,
                executadoEm,
                totalVendasProcessadas,
                resultados.size(),
                totalImpedimentos,
                totalVendas,
                totalComissao,
                totalBonusPerc,
                totalBonusFixo,
                totalBonusGeral,
                totalComissao, // Valor líquido total a pagar aos vendedores
                taxaMediaEfetivaGeral,
                colaboradoresDistintos.size(),
                lojasDistintas.size(),
                canaisDistintos.size()
        );

        // Consolida Lista por Canal
        final BigDecimal totalVendasFinal = totalVendas;
        final BigDecimal totalComissaoFinal = totalComissao;

        List<ConsolidadoCanalDTO> porCanal = canalMap.values().stream()
                .map(acc -> acc.toDTO(totalVendasFinal, totalComissaoFinal))
                .sorted(Comparator.comparing(ConsolidadoCanalDTO::valorTotalComissao).reversed())
                .toList();

        // Consolida Lista por Equipe
        List<ConsolidadoEquipeDTO> porEquipe = equipeMap.values().stream()
                .map(acc -> acc.toDTO(totalComissaoFinal))
                .sorted(Comparator.comparing(ConsolidadoEquipeDTO::valorTotalComissao).reversed())
                .toList();

        // Consolida Lista por Colaborador
        List<ConsolidadoColaboradorDTO> porColaborador = colaboradorMap.values().stream()
                .map(ColaboradorAccumulator::toDTO)
                .sorted(Comparator.comparing(ConsolidadoColaboradorDTO::valorTotalComissao).reversed())
                .toList();

        return new RelatorioFechamentoConsolidadoDTO(
                resumo,
                porCanal,
                porEquipe,
                porColaborador,
                itensDetalhados
        );
    }

    private void exportarRelatorioCompleto(PrintWriter w, RelatorioFechamentoConsolidadoDTO rel, String d) {
        ResumoFechamentoDTO r = rel.resumo();
        w.println("# RELATORIO CONSOLIDADO DE FECHAMENTO FINANCEIRO");
        w.println("Identificador" + d + escapeCsv(r.identificador()));
        w.println("Protocolo" + d + r.protocolo());
        w.println("Status" + d + r.status());
        w.println("Competencia" + d + r.competencia());
        w.println("Periodo" + d + r.dataInicio() + " a " + r.dataFim());
        w.println("Executado Por" + d + escapeCsv(r.executadoPor()));
        w.println("Executado Em" + d + r.executadoEm());
        w.println("Total Vendas Processadas" + d + r.totalVendasProcessadas());
        w.println("Total Vendas Liquidadas" + d + r.totalCalculados());
        w.println("Total Impedimentos" + d + r.totalImpedimentos());
        w.println("Valor Total Vendas (R$)" + d + formatarMoeda(r.valorTotalVendas()));
        w.println("Valor Total Comissoes (R$)" + d + formatarMoeda(r.valorTotalComissao()));
        w.println("Valor Total Bonus (R$)" + d + formatarMoeda(r.valorTotalBonus()));
        w.println("Taxa Media Efetiva" + d + formatarTaxa(r.taxaMediaEfetiva()));
        w.println("Colaboradores Contemplados" + d + r.totalColaboradoresDistintos());
        w.println("Lojas Contempladas" + d + r.totalLojasDistintas());
        w.println("Canais de Venda" + d + r.totalCanaisDistintos());
        w.println();

        w.println("# CONSOLIDADO POR CANAL DE VENDA");
        exportarSecaoCanal(w, rel, d);
        w.println();

        w.println("# CONSOLIDADO POR EQUIPE (LOJA E MARCA)");
        exportarSecaoEquipe(w, rel, d);
        w.println();

        w.println("# CONSOLIDADO POR COLABORADOR");
        exportarSecaoColaborador(w, rel, d);
        w.println();

        w.println("# DETALHAMENTO DE VENDAS LIQUIDADAS");
        exportarSecaoDetalhada(w, rel, d);
    }

    private void exportarSecaoCanal(PrintWriter w, RelatorioFechamentoConsolidadoDTO rel, String d) {
        w.println(String.join(d,
                "Canal",
                "QtdVendas",
                "QtdColaboradores",
                "ValorTotalVendas",
                "ValorTotalComissao",
                "ValorTotalBonus",
                "TaxaMediaEfetiva",
                "PercSobreTotalVendas",
                "PercSobreTotalComissao"
        ));
        for (ConsolidadoCanalDTO c : rel.porCanal()) {
            w.println(String.join(d,
                    escapeCsv(c.canal()),
                    String.valueOf(c.quantidadeVendas()),
                    String.valueOf(c.quantidadeColaboradores()),
                    formatarMoeda(c.valorTotalVendas()),
                    formatarMoeda(c.valorTotalComissao()),
                    formatarMoeda(c.valorTotalBonus()),
                    formatarTaxa(c.taxaMediaEfetiva()),
                    formatarPercentual(c.percentualSobreTotalVendas()),
                    formatarPercentual(c.percentualSobreTotalComissao())
            ));
        }
    }

    private void exportarSecaoEquipe(PrintWriter w, RelatorioFechamentoConsolidadoDTO rel, String d) {
        w.println(String.join(d,
                "CodLoja",
                "NomeLoja",
                "CodMarca",
                "NomeMarca",
                "QtdVendas",
                "QtdColaboradores",
                "ValorTotalVendas",
                "ValorTotalComissao",
                "ValorTotalBonus",
                "TaxaMediaEfetiva",
                "PercSobreTotalComissao"
        ));
        for (ConsolidadoEquipeDTO e : rel.porEquipe()) {
            w.println(String.join(d,
                    String.valueOf(e.codLoja() != null ? e.codLoja() : ""),
                    escapeCsv(e.nomeLoja()),
                    String.valueOf(e.codMarca() != null ? e.codMarca() : ""),
                    escapeCsv(e.nomeMarca()),
                    String.valueOf(e.quantidadeVendas()),
                    String.valueOf(e.quantidadeColaboradores()),
                    formatarMoeda(e.valorTotalVendas()),
                    formatarMoeda(e.valorTotalComissao()),
                    formatarMoeda(e.valorTotalBonus()),
                    formatarTaxa(e.taxaMediaEfetiva()),
                    formatarPercentual(e.percentualSobreTotalComissao())
            ));
        }
    }

    private void exportarSecaoColaborador(PrintWriter w, RelatorioFechamentoConsolidadoDTO rel, String d) {
        w.println(String.join(d,
                "Matricula",
                "CodCargo",
                "NomeCargo",
                "CodLoja",
                "NomeLoja",
                "CodMarca",
                "NomeMarca",
                "QtdVendas",
                "ValorTotalVendas",
                "ValorTotalComissao",
                "BonusPercentual",
                "BonusFixo",
                "TotalBonus",
                "ValorTotalAPagar",
                "TaxaMediaEfetiva"
        ));
        for (ConsolidadoColaboradorDTO col : rel.porColaborador()) {
            w.println(String.join(d,
                    escapeCsv(col.matricula()),
                    String.valueOf(col.codCargo() != null ? col.codCargo() : ""),
                    escapeCsv(col.nomeCargo()),
                    String.valueOf(col.codLoja() != null ? col.codLoja() : ""),
                    escapeCsv(col.nomeLoja()),
                    String.valueOf(col.codMarca() != null ? col.codMarca() : ""),
                    escapeCsv(col.nomeMarca()),
                    String.valueOf(col.quantidadeVendas()),
                    formatarMoeda(col.valorTotalVendas()),
                    formatarMoeda(col.valorTotalComissao()),
                    formatarMoeda(col.valorTotalBonusPercentual()),
                    formatarMoeda(col.valorTotalBonusFixo()),
                    formatarMoeda(col.valorTotalBonus()),
                    formatarMoeda(col.valorTotalAPagar()),
                    formatarTaxa(col.taxaMediaEfetiva())
            ));
        }
    }

    private void exportarSecaoDetalhada(PrintWriter w, RelatorioFechamentoConsolidadoDTO rel, String d) {
        w.println(String.join(d,
                "ProtocoloCalculo",
                "IdVenda",
                "DataVenda",
                "Matricula",
                "CodLoja",
                "NomeLoja",
                "CodMarca",
                "NomeMarca",
                "CodCargo",
                "NomeCargo",
                "Canal",
                "ValorVenda",
                "TaxaAplicada",
                "ValorComissaoBase",
                "ValorBonusPercentual",
                "ValorBonusFixo",
                "ValorTotalComissao",
                "RegraId",
                "OrigemTaxa",
                "VersaoCampanha"
        ));
        for (ItemRelatorioFechamentoDTO i : rel.detalheVendas()) {
            w.println(String.join(d,
                    i.protocoloCalculo() != null ? i.protocoloCalculo().toString() : "",
                    i.idVenda() != null ? i.idVenda().toString() : "",
                    i.dataVenda() != null ? i.dataVenda().toString() : "",
                    escapeCsv(i.matricula()),
                    String.valueOf(i.codLoja() != null ? i.codLoja() : ""),
                    escapeCsv(i.nomeLoja()),
                    String.valueOf(i.codMarca() != null ? i.codMarca() : ""),
                    escapeCsv(i.nomeMarca()),
                    String.valueOf(i.codCargo() != null ? i.codCargo() : ""),
                    escapeCsv(i.nomeCargo()),
                    escapeCsv(i.canal()),
                    formatarMoeda(i.valorVenda()),
                    formatarTaxa(i.taxaAplicada()),
                    formatarMoeda(i.valorComissao()),
                    formatarMoeda(i.valorBonusPercentual()),
                    formatarMoeda(i.valorBonusFixo()),
                    formatarMoeda(i.valorTotalComissao()),
                    String.valueOf(i.regraId() != null ? i.regraId() : ""),
                    escapeCsv(i.origemTaxa()),
                    String.valueOf(i.versaoCampanhaNumero() != null ? i.versaoCampanhaNumero() : "")
            ));
        }
    }

    private String escapeCsv(String valor) {
        if (valor == null) return "";
        if (valor.contains(";") || valor.contains(",") || valor.contains("\"") || valor.contains("\n") || valor.contains("\r")) {
            return "\"" + valor.replace("\"", "\"\"") + "\"";
        }
        return valor;
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) return "0.00";
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatarTaxa(BigDecimal taxa) {
        if (taxa == null) return "0.0000";
        return taxa.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatarPercentual(BigDecimal perc) {
        if (perc == null) return "0.00%";
        return perc.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String normalizarCompetencia(String comp) {
        if (comp == null || comp.isBlank()) {
            throw new BusinessException("A competência é obrigatória para o relatório de fechamento.");
        }
        String limpo = comp.trim();
        if (limpo.length() >= 7) {
            return limpo.substring(0, 7);
        }
        throw new BusinessException(String.format("Formato de competência inválido: '%s'. Utilize YYYY-MM.", comp));
    }

    // Classes acumuladoras em memória para agregação limpa e eficiente
    private static class CanalAccumulator {
        private final String canal;
        private int qtdVendas = 0;
        private final Set<String> vendedores = new HashSet<>();
        private BigDecimal totalVendas = BigDecimal.ZERO;
        private BigDecimal totalComissao = BigDecimal.ZERO;
        private BigDecimal totalBonus = BigDecimal.ZERO;

        public CanalAccumulator(String canal) {
            this.canal = canal;
        }

        public void acumular(BigDecimal vVenda, BigDecimal vComissao, BigDecimal vBonus, String matricula) {
            this.qtdVendas++;
            if (matricula != null) this.vendedores.add(matricula);
            this.totalVendas = this.totalVendas.add(vVenda);
            this.totalComissao = this.totalComissao.add(vComissao);
            this.totalBonus = this.totalBonus.add(vBonus);
        }

        public ConsolidadoCanalDTO toDTO(BigDecimal totalGeralVendas, BigDecimal totalGeralComissao) {
            BigDecimal taxaMedia = totalVendas.compareTo(BigDecimal.ZERO) > 0
                    ? totalComissao.divide(totalVendas, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

            BigDecimal percVendas = totalGeralVendas != null && totalGeralVendas.compareTo(BigDecimal.ZERO) > 0
                    ? totalVendas.multiply(BigDecimal.valueOf(100)).divide(totalGeralVendas, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal percComissao = totalGeralComissao != null && totalGeralComissao.compareTo(BigDecimal.ZERO) > 0
                    ? totalComissao.multiply(BigDecimal.valueOf(100)).divide(totalGeralComissao, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            return new ConsolidadoCanalDTO(
                    canal,
                    qtdVendas,
                    vendedores.size(),
                    totalVendas.setScale(2, RoundingMode.HALF_UP),
                    totalComissao.setScale(2, RoundingMode.HALF_UP),
                    totalBonus.setScale(2, RoundingMode.HALF_UP),
                    taxaMedia,
                    percVendas,
                    percComissao
            );
        }
    }

    private static class EquipeAccumulator {
        private final Integer codLoja;
        private final String nomeLoja;
        private final Integer codMarca;
        private final String nomeMarca;
        private int qtdVendas = 0;
        private final Set<String> colaboradores = new HashSet<>();
        private BigDecimal totalVendas = BigDecimal.ZERO;
        private BigDecimal totalComissao = BigDecimal.ZERO;
        private BigDecimal totalBonus = BigDecimal.ZERO;

        public EquipeAccumulator(Integer codLoja, String nomeLoja, Integer codMarca, String nomeMarca) {
            this.codLoja = codLoja;
            this.nomeLoja = nomeLoja;
            this.codMarca = codMarca;
            this.nomeMarca = nomeMarca;
        }

        public void acumular(BigDecimal vVenda, BigDecimal vComissao, BigDecimal vBonus, String matricula) {
            this.qtdVendas++;
            if (matricula != null) this.colaboradores.add(matricula);
            this.totalVendas = this.totalVendas.add(vVenda);
            this.totalComissao = this.totalComissao.add(vComissao);
            this.totalBonus = this.totalBonus.add(vBonus);
        }

        public ConsolidadoEquipeDTO toDTO(BigDecimal totalGeralComissao) {
            BigDecimal taxaMedia = totalVendas.compareTo(BigDecimal.ZERO) > 0
                    ? totalComissao.divide(totalVendas, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

            BigDecimal percComissao = totalGeralComissao != null && totalGeralComissao.compareTo(BigDecimal.ZERO) > 0
                    ? totalComissao.multiply(BigDecimal.valueOf(100)).divide(totalGeralComissao, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            return new ConsolidadoEquipeDTO(
                    codLoja,
                    nomeLoja,
                    codMarca,
                    nomeMarca,
                    qtdVendas,
                    colaboradores.size(),
                    totalVendas.setScale(2, RoundingMode.HALF_UP),
                    totalComissao.setScale(2, RoundingMode.HALF_UP),
                    totalBonus.setScale(2, RoundingMode.HALF_UP),
                    taxaMedia,
                    percComissao
            );
        }
    }

    private static class ColaboradorAccumulator {
        private final String matricula;
        private final Integer codCargo;
        private final String nomeCargo;
        private final Integer codLoja;
        private final String nomeLoja;
        private final Integer codMarca;
        private final String nomeMarca;
        private int qtdVendas = 0;
        private BigDecimal totalVendas = BigDecimal.ZERO;
        private BigDecimal totalComissao = BigDecimal.ZERO;
        private BigDecimal totalBonusPerc = BigDecimal.ZERO;
        private BigDecimal totalBonusFixo = BigDecimal.ZERO;

        public ColaboradorAccumulator(String matricula, Integer codCargo, String nomeCargo,
                                      Integer codLoja, String nomeLoja, Integer codMarca, String nomeMarca) {
            this.matricula = matricula;
            this.codCargo = codCargo;
            this.nomeCargo = nomeCargo;
            this.codLoja = codLoja;
            this.nomeLoja = nomeLoja;
            this.codMarca = codMarca;
            this.nomeMarca = nomeMarca;
        }

        public void acumular(BigDecimal vVenda, BigDecimal vComissao, BigDecimal vBonusPerc, BigDecimal vBonusFixo) {
            this.qtdVendas++;
            this.totalVendas = this.totalVendas.add(vVenda);
            this.totalComissao = this.totalComissao.add(vComissao);
            this.totalBonusPerc = this.totalBonusPerc.add(vBonusPerc);
            this.totalBonusFixo = this.totalBonusFixo.add(vBonusFixo);
        }

        public ConsolidadoColaboradorDTO toDTO() {
            BigDecimal taxaMedia = totalVendas.compareTo(BigDecimal.ZERO) > 0
                    ? totalComissao.divide(totalVendas, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

            BigDecimal totalBonus = totalBonusPerc.add(totalBonusFixo).setScale(2, RoundingMode.HALF_UP);

            return new ConsolidadoColaboradorDTO(
                    matricula,
                    codCargo,
                    nomeCargo,
                    codLoja,
                    nomeLoja,
                    codMarca,
                    nomeMarca,
                    qtdVendas,
                    totalVendas.setScale(2, RoundingMode.HALF_UP),
                    totalComissao.setScale(2, RoundingMode.HALF_UP),
                    totalBonusPerc.setScale(2, RoundingMode.HALF_UP),
                    totalBonusFixo.setScale(2, RoundingMode.HALF_UP),
                    totalBonus,
                    totalComissao.setScale(2, RoundingMode.HALF_UP),
                    taxaMedia
            );
        }
    }
}

