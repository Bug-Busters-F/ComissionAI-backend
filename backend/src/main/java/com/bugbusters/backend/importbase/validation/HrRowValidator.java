package com.bugbusters.backend.importbase.validation;

import com.bugbusters.backend.importbase.dto.HrFileRow;
import com.bugbusters.backend.registration.Registration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HrRowValidator {

    public List<ErroValidacaoLinhaDTO> validar(HrFileRow row) {
        return validar(row, null, null);
    }

    public List<ErroValidacaoLinhaDTO> validar(HrFileRow row, Set<String> matriculasNoArquivo, Map<String, Registration> matriculasExistentesBanco) {
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
        if (row.getPositionCode() == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("positionCode", "Campo obrigatório ausente ou inválido: código do cargo"));
        }
        if (row.getAdmissDate() == null) {
            erros.add(ErroValidacaoLinhaDTO.impeditivo("admissDate", "Campo obrigatório ausente ou inválido: data de admissão"));
        }

        // 2. Coerência cronológica
        if (row.getAdmissDate() != null && row.getDemissDate() != null) {
            if (row.getDemissDate().isBefore(row.getAdmissDate())) {
                erros.add(ErroValidacaoLinhaDTO.impeditivo("demissDate", "Data de demissão não pode ser anterior à data de admissão"));
            }
        }

        // 3. Validações contextuais adicionais para a área de preparação
        if (row.getRegistration() != null && !row.getRegistration().isBlank()) {
            String mat = row.getRegistration().trim();

            if (matriculasNoArquivo != null && matriculasNoArquivo.contains(mat)) {
                erros.add(ErroValidacaoLinhaDTO.aviso("matricula", "Matrícula repetida dentro do mesmo arquivo: " + mat));
            }

            if (matriculasExistentesBanco != null && matriculasExistentesBanco.containsKey(mat)) {
                Registration existente = matriculasExistentesBanco.get(mat);
                boolean diverge = (row.getStoreCode() != null && (existente.getStore() == null || !row.getStoreCode().equals(existente.getStore().getCode())))
                        || (row.getPositionCode() != null && (existente.getPosition() == null || !row.getPositionCode().equals(existente.getPosition().getCode())))
                        || (row.getAdmissDate() != null && !row.getAdmissDate().equals(existente.getAdmissDate()))
                        || (row.getDemissDate() != null && !row.getDemissDate().equals(existente.getDemissDate()));

                if (diverge) {
                    erros.add(ErroValidacaoLinhaDTO.aviso("matricula", "Matrícula já cadastrada no sistema. A efetivação atualizará os dados cadastrais da matrícula " + mat));
                } else {
                    erros.add(ErroValidacaoLinhaDTO.aviso("matricula", "Matrícula já cadastrada no sistema com dados idênticos: " + mat));
                }
            }
        }

        return erros;
    }
}
