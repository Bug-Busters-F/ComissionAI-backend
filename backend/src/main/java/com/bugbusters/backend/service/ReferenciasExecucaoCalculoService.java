package com.bugbusters.backend.service;

import com.bugbusters.backend.dto.regra.StatusRegra;
import com.bugbusters.backend.exception.BusinessException;
import com.bugbusters.backend.model.Regra;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import com.bugbusters.backend.repository.RegraRepository;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.YearMonth;
import java.util.*;

/** Produz as assinaturas que congelam as entradas de uma execução assíncrona. */
@Service
public class ReferenciasExecucaoCalculoService {
    private final SaleRepository saleRepository; private final RegraRepository regraRepository; private final CoberturaBaseService cobertura;
    public ReferenciasExecucaoCalculoService(SaleRepository s, RegraRepository r, CoberturaBaseService c) { saleRepository=s; regraRepository=r; cobertura=c; }
    public Referencias validar(String competencia) {
        YearMonth ym;
        try { ym=YearMonth.parse(competencia); } catch (Exception e) { throw new BusinessException("Competência inválida: " + competencia); }
        List<Sale> vendas = saleRepository.findBySaleDateBetweenOrderBySaleDateAsc(ym.atDay(1), ym.atEndOfMonth());
        if (cobertura != null && !vendas.isEmpty()) cobertura.validarCobertura(vendas, ym);
        List<Regra> regras = regraRepository.findRegrasAtivasNoPeriodo(ym.atDay(1), ym.atEndOfMonth(), StatusRegra.ATIVA);
        String dados = sha(vendas.stream().map(s -> String.valueOf(s.getId()) + "|" + s.getSaleDate() + "|" + s.getValue()).sorted().reduce("", (a,b)->a+b+";"));
        String regrasHash = sha(regras.stream().map(r -> r.getId()+"|"+r.getTaxa()+"|"+r.getDataInicio()+"|"+r.getDataFim()).sorted().reduce("", (a,b)->a+b+";"));
        return new Referencias(vendas.size(), dados, regrasHash);
    }
    private String sha(String value) { try { byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(b); } catch(Exception e){throw new IllegalStateException(e);} }
    public record Referencias(int totalItens, String dados, String regras) {}
}
