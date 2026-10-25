package com.bugbusters.backend.service;

import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.*;
import com.bugbusters.backend.repository.*;
import com.bugbusters.backend.sales.SaleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.*;

/** Canonical references to the actual sale, HR, contractual base, cached results and active rule inputs. */
@Service
public class ReferenciasExecucaoCalculoService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SaleRepository sales;
    private final RegraRepository rules;
    private final BaseComissRepository bases;
    private final ResultadoCalculoRepository results;
    private final CoberturaBaseService coverage;
    private final FechamentoCompetenciaRepository closings;

    public ReferenciasExecucaoCalculoService(SaleRepository sales, RegraRepository rules, BaseComissRepository bases,
            ResultadoCalculoRepository results, CoberturaBaseService coverage, FechamentoCompetenciaRepository closings) {
        this.sales=sales; this.rules=rules; this.bases=bases; this.results=results; this.coverage=coverage; this.closings=closings;
    }
    public void validarDisponivel(String competencia) {
        if (closings.existsByCompetenciaAndStatus(competencia,StatusFechamento.CONCLUIDO))
            throw new BusinessException("A competência " + competencia + " possui fechamento final concluído. Consulte os resultados históricos.");
        if (closings.existsByCompetenciaAndStatus(competencia,StatusFechamento.PROCESSANDO))
            throw new BusinessException("A competência " + competencia + " está em processo de fechamento final.");
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Referencias validar(String competencia) {
        YearMonth month;
        try { month=YearMonth.parse(competencia); }
        catch (Exception e) { throw new BusinessException("Competência inválida: " + competencia); }
        validarDisponivel(competencia);
        var vendas=sales.referencias(month.atDay(1),month.atEndOfMonth());
        if (!vendas.isEmpty()) coverage.validarCobertura(vendas,month);
        var dados=new ArrayList<String>();
        var pares=new HashSet<String>();
        for (var sale:vendas) {
            var person=sale.getRegistration(); var position=person != null ? person.getPosition():null;
            var brand=sale.getBrand(); var store=sale.getStore(); var personStore=person != null ? person.getStore():null;
            dados.add(row("venda",sale.getId(),sale.getSaleDate(),sale.getValue(),sale.getSaleChannel(),sale.getTipoVenda(),sale.getIdLoteOrigem(),
                brand != null ? brand.getId():null,brand != null ? brand.getCode():null,brand != null ? brand.getDescription():null,
                store != null ? store.getId():null,store != null ? store.getCode():null,store != null ? store.getDescription():null,
                person != null ? person.getId():null,person != null ? person.getRegistration():null,person != null ? person.getAdmissDate():null,person != null ? person.getDemissDate():null,
                position != null ? position.getId():null,position != null ? position.getCode():null,position != null ? position.getDescription():null,
                personStore != null ? personStore.getId():null,personStore != null ? personStore.getCode():null,personStore != null ? personStore.getDescription():null));
            if (brand != null && position != null) pares.add(row(brand.getCode(),position.getCode()));
        }
        // The resolver may fall back to another reference month for the same pair, so retain every base of consulted pairs.
        if (!pares.isEmpty()) for (var base:bases.findAllComRelacionamentos()) {
            if (pares.contains(row(base.getBrand().getCode(),base.getPosition().getCode())))
                dados.add(row("base",base.getId(),base.getReferenceMonth(),base.getPercentage(),base.getIdLoteOrigem(),
                    base.getBrand().getId(),base.getBrand().getCode(),base.getBrand().getDescription(),
                    base.getPosition().getId(),base.getPosition().getCode(),base.getPosition().getDescription()));
        }
        if (!vendas.isEmpty()) for (var r:results.findByIdVendaIn(vendas.stream().map(s -> s.getId()).toList()))
            dados.add(row("resultado",r.getIdVenda(),r.getValorVenda(),r.getValorComissao(),r.getTaxaAplicada(),r.getRegraId(),r.getOrigemTaxa(),
                r.getValorBonusPercentual(),r.getValorBonusFixo(),r.getVersaoCampanhaNumero(),r.getFechamentoId(),r.getProtocoloCalculo(),
                r.getMatricula(),r.getCodMarca(),r.getCodLoja(),r.getCodCargo(),r.getDataVenda(),r.getCalculadoEm(),r.getTipoCalculo()));
        var regras=new ArrayList<String>();
        for (var r:rules.findRegrasAtivasNoPeriodo(month.atDay(1),month.atEndOfMonth(),StatusRegra.ATIVA)) {
            var c=r.getCampanha(); var vigente=c != null ? c.getVersaoVigente():null;
            regras.add(row(r.getId(),r.getNome(),r.getStatus(),r.getRemovidoEm(),r.getCanal(),r.getCodMarca(),r.getDescrMarca(),r.getCodLoja(),r.getCodCargo(),r.getDescriCargo(),r.getMatricula(),
                r.getTaxa(),r.getDataInicio(),r.getDataFim(),r.getValorMinimo(),r.getMinInclusivo(),r.getValorMaximo(),r.getMaxInclusivo(),
                r.getTipoOperacao(),r.getValorAjuste(),r.getTipoBaseReferencia(),r.getTaxaBaseConsultada(),r.getDescricaoReferencia(),r.getBonusPercentual(),r.getBonusValorFixo(),r.getCompleta(),
                r.getCampanhaVersao() != null ? r.getCampanhaVersao().getId():null,
                c != null ? c.getId():null,c != null ? c.getEstado():null,c != null ? c.getRemovidoEm():null,
                c != null ? c.getDataInicio():null,c != null ? c.getDataFim():null,
                vigente != null ? vigente.getId():null,vigente != null ? vigente.getNumero():null));
        }
        return new Referencias(vendas.size(),sha("dados:v2",dados),sha("regras:v2",regras));
    }
    private static String row(Object... values) {
        try {
            var canonical=new ArrayList<String>();
            for (Object v:values) canonical.add(v == null ? null : v instanceof BigDecimal number ? number.stripTrailingZeros().toPlainString() : v.toString());
            return JSON.writeValueAsString(canonical);
        } catch (Exception e) { throw new IllegalStateException("Não foi possível gerar a referência do cálculo.",e); }
    }
    private static String sha(String namespace,List<String> rows) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); digest.update(namespace.getBytes(StandardCharsets.UTF_8));
            rows.sort(String::compareTo);
            for (String row:rows) { byte[] bytes=row.getBytes(StandardCharsets.UTF_8); digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public record Referencias(int totalItens,String dados,String regras) {}
}
