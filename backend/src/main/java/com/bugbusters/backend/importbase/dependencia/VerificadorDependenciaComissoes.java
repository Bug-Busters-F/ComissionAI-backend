package com.bugbusters.backend.importbase.dependencia;

import com.bugbusters.backend.basecomiss.BaseComiss;
import com.bugbusters.backend.basecomiss.BaseComissRepository;
import com.bugbusters.backend.model.EnvioArquivo;
import com.bugbusters.backend.repository.LogCalculoRepository;
import com.bugbusters.backend.repository.ResultadoCalculoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class VerificadorDependenciaComissoes implements VerificadorDependenciaLote {

    private final BaseComissRepository baseComissRepository;
    private final ResultadoCalculoRepository resultadoCalculoRepository;
    private final LogCalculoRepository logCalculoRepository;

    public VerificadorDependenciaComissoes(BaseComissRepository baseComissRepository,
                                           ResultadoCalculoRepository resultadoCalculoRepository,
                                           LogCalculoRepository logCalculoRepository) {
        this.baseComissRepository = baseComissRepository;
        this.resultadoCalculoRepository = resultadoCalculoRepository;
        this.logCalculoRepository = logCalculoRepository;
    }

    @Override
    public List<DependenciaMotivoDTO> verificar(EnvioArquivo lote) {
        if (lote == null || !"COMISSIONS".equalsIgnoreCase(lote.getTipoBase())) {
            return List.of();
        }

        List<DependenciaMotivoDTO> motivos = new ArrayList<>();
        String idLote = lote.getIdLote();
        if (idLote == null || idLote.isBlank()) {
            return motivos;
        }

        List<BaseComiss> bases = baseComissRepository.findByIdLoteOrigem(idLote);
        if (bases.isEmpty()) {
            return motivos;
        }

        String compStr = lote.getCompetencia();
        if (compStr == null || compStr.isBlank()) {
            LocalDate ref = bases.get(0).getReferenceMonth();
            if (ref != null) {
                compStr = YearMonth.from(ref).toString();
            }
        }

        if (compStr == null || compStr.isBlank()) {
            return motivos;
        }

        YearMonth ym = YearMonth.parse(compStr);
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        long totalCalculos = 0;
        long totalLogs = 0;
        Set<String> paresComCalculo = new HashSet<>();
        Set<String> paresComLog = new HashSet<>();

        for (BaseComiss b : bases) {
            Integer codMarca = b.getBrand() != null ? b.getBrand().getCode() : null;
            Integer codCargo = b.getPosition() != null ? b.getPosition().getCode() : null;

            if (codMarca != null && codCargo != null) {
                long cCalc = resultadoCalculoRepository.countByMarcaCargoEPeriodo(codMarca, codCargo, inicio, fim);
                if (cCalc > 0) {
                    totalCalculos += cCalc;
                    paresComCalculo.add("marca:" + codMarca + "/cargo:" + codCargo);
                }

                long cLog = logCalculoRepository.countByMarcaCargoEPeriodo(codMarca, codCargo, inicio, fim);
                if (cLog > 0) {
                    totalLogs += cLog;
                    paresComLog.add("marca:" + codMarca + "/cargo:" + codCargo);
                }
            }
        }

        // TODO (BACK-7): Registrar verificação de simulações persistidas quando implementadas.

        if (totalCalculos > 0) {
            List<String> exemplos = paresComCalculo.stream().limit(5).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "calculos_competencia",
                    String.format("Existem %d resultado(s) de cálculo realizados na competência %s dependentes das taxas base deste lote. Exemplos: %s",
                            totalCalculos, compStr, exemplos)
            ));
        }

        if (totalLogs > 0) {
            List<String> exemplos = paresComLog.stream().limit(5).toList();
            motivos.add(new DependenciaMotivoDTO(
                    "logs_competencia",
                    String.format("Existem %d log(s) financeiros realizados na competência %s dependentes das taxas base deste lote. Exemplos: %s",
                            totalLogs, compStr, exemplos)
            ));
        }

        return motivos;
    }
}
