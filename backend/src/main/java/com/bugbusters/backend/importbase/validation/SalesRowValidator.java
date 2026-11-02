package com.bugbusters.backend.importbase.validation;

import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.registration.Registration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SalesRowValidator {

    public List<ErroValidacaoLinhaDTO> validar(SalesFileRow row, YearMonth competenciaEsperada) {
        return validar(row, competenciaEsperada, null, null);
    }

    public List<ErroValidacaoLinhaDTO> validar(
            SalesFileRow row,
            YearMonth competenciaEsperada,
            Map<String, Registration> colaboradoresMap,
            Set<String> chavesVendasArquivo
    ) {
        List<ErroValidacaoLinhaDTO> erros = new ArrayList<>();

        if (row == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("linha", "Linha vazia ou não informada."));
            return erros;
        }

        // 1. Campos obrigatórios (mensagens idênticas ao ImportTasklet)
        if (row.getRegistration() == null || row.getRegistration().isBlank()) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("matricula", "Campo obrigatório ausente ou inválido: matrícula"));
        }
        if (row.getStoreCode() == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("storeCode", "Campo obrigatório ausente ou inválido: código da loja"));
        }
        if (row.getBrandCode() == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("brandCode", "Campo obrigatório ausente ou inválido: código da marca"));
        }
        if (row.getReferenceDate() == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("referenceDate", "Campo obrigatório ausente ou inválido: data de referência"));
        }
        if (row.getSaleValue() == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("saleValue", "Campo obrigatório ausente ou inválido: valor da venda"));
        }

        // 2. Divergência de competência
        if (row.getReferenceDate() != null && competenciaEsperada != null) {
            if (!YearMonth.from(row.getReferenceDate()).equals(competenciaEsperada)) {
                erros.add(ErroValidacaoLinhaDTO.impeditivo("referenceDate", String.format(
                        "Linha com data de referência '%s' diverge da competência informada '%s' para o lote de vendas.",
                        row.getReferenceDate(), competenciaEsperada
                )));
            }
        }

        // 3. Valor de venda positivo
        if (row.getSaleValue() != null && row.getSaleValue().compareTo(BigDecimal.ZERO) <= 0) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("saleValue", "Valor da venda deve ser maior que zero"));
        }

        // 4. Verificação de cadastro de matrícula e vigência contratual
        if (colaboradoresMap != null && row.getRegistration() != null && !row.getRegistration().isBlank()) {
            String mat = row.getRegistration().trim();
            Registration reg = colaboradoresMap.get(mat);
            if (reg == null) {
                erros.add(ErroValidacaoLinhaDTO.advertencia("matricula", "Matrícula não cadastrada: " + mat));
            } else if (row.getReferenceDate() != null) {
                if (reg.getAdmissDate() != null && reg.getAdmissDate().isAfter(row.getReferenceDate())) {
                    erros.add(ErroValidacaoLinhaDTO.aviso("referenceDate", String.format(
                            "Data da venda (%s) anterior à admissão do colaborador (%s)",
                            row.getReferenceDate(), reg.getAdmissDate()
                    )));
                }
                if (reg.getDemissDate() != null && reg.getDemissDate().isBefore(row.getReferenceDate())) {
                    erros.add(ErroValidacaoLinhaDTO.aviso("referenceDate", String.format(
                            "Data da venda (%s) posterior à demissão do colaborador (%s)",
                            row.getReferenceDate(), reg.getDemissDate()
                    )));
                }
            }
        }

        // 5. Duplicidade no mesmo arquivo
        if (chavesVendasArquivo != null && row.getRegistration() != null && row.getReferenceDate() != null
                && row.getStoreCode() != null && row.getBrandCode() != null && row.getSaleValue() != null) {
            String chave = String.format("%s:%s:%d:%d:%s",
                    row.getRegistration().trim(),
                    row.getReferenceDate(),
                    row.getStoreCode(),
                    row.getBrandCode(),
                    row.getSaleValue()
            );
            if (chavesVendasArquivo.contains(chave)) {
                erros.add(ErroValidacaoLinhaDTO.aviso("venda", "Possível duplicidade: venda com mesmos valores identificada no mesmo arquivo"));
            }
        }

        return erros;
    }
}
