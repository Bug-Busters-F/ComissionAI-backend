package com.bugbusters.backend.importbase.dependencia;

import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.model.LogCalculoImutavel;
import com.bugbusters.backend.model.ResultadoCalculo;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import com.bugbusters.backend.sales.Sale;
import com.bugbusters.backend.sales.SaleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class VerificadorDependenciaVendas implements VerificadorDependenciaLote {

    private final SaleRepository saleRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;

    public VerificadorDependenciaVendas(SaleRepository saleRepository,
                                        ResultadoCalculoRepository resultadoCalculoRepository,
                                        LogCalculoRepository logCalculoRepository) {
        this.saleRepository = saleRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
    }

    @Override
    public List<DependenciaMotivoDTO> verificar(EnvioArquivo lote) {
        if (lote == null || !"SALES".equalsIgnoreCase(lote.getTipoBase())) {
            return List.of();
        }

        List<DependenciaMotivoDTO> motivos = new ArrayList<>();
        String idLote = lote.getIdLote();
        if (idLote == null || idLote.isBlank()) {
            return motivos;
        }

        List<Sale> vendas = saleRepository.findByIdLoteOrigem(idLote);
        List<UUID> idsVendas = vendas.stream().map(Sale::getId).toList();

        // 1. Resultados de cálculo vinculados aos id_venda das vendas do lote
        if (!idsVendas.isEmpty() && resultadoCalculoRepository.existsByIdVendaIn(idsVendas)) {
            List<ResultadoCalculo> top5 = resultadoCalculoRepository.findTop5ByIdVendaIn(idsVendas);
            List<String> exemplos = top5.stream().map(r -> String.valueOf(r.getIdVenda())).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "calculos",
                    String.format("Existem resultado(s) de cálculo vinculados às vendas deste lote. Exemplos de idVenda: %s", exemplos)
            ));
        }

        // 2. Logs financeiros vinculados aos id_venda ou com id_lote_origem igual ao do lote
        boolean existeLogPorVenda = !idsVendas.isEmpty() && logCalculoRepository.existsByIdVendaIn(idsVendas);
        boolean existeLogPorLote = logCalculoRepository.existsByIdLoteOrigem(idLote);

        if (existeLogPorVenda || existeLogPorLote) {
            List<LogCalculoImutavel> top5 = existeLogPorVenda
                    ? logCalculoRepository.findTop5ByIdVendaIn(idsVendas)
                    : logCalculoRepository.findTop5ByIdLoteOrigem(idLote);
            List<String> exemplos = top5.stream().map(l -> String.valueOf(l.getProtocolo())).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "logs_financeiros",
                    String.format("Existem log(s) de auditoria financeira vinculados às vendas ou ao lote de origem. Exemplos de protocolo: %s", exemplos)
            ));
        }

        return motivos;
    }
}
