package com.bugbusters.backend.importbase.validation;

import com.bugbusters.backend.importbase.ImportInconsistencySeverity;
import com.bugbusters.backend.importbase.dto.HrFileRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HrRowValidatorTest {

    private HrRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HrRowValidator();
    }

    @Test
    @DisplayName("Linha válida de RH não deve gerar inconsistências")
    void linhaValida_semErros() {
        HrFileRow row = new HrFileRow(
                LocalDate.of(2023, 5, 1),
                10,
                "Marca Alpha",
                20,
                "Loja Centro",
                "M1001",
                LocalDate.of(2023, 1, 15),
                null,
                30,
                "Consultor"
        );

        List<ErroValidacaoLinhaDTO> erros = validator.validar(row);

        assertThat(erros).isEmpty();
    }

    @Test
    @DisplayName("Campos obrigatórios ausentes devem gerar erro IMPEDITIVO")
    void camposObrigatoriosAusentes_geraImpeditivo() {
        HrFileRow row = new HrFileRow(
                LocalDate.of(2023, 5, 1),
                null,
                null,
                null,
                null,
                "",
                null,
                null,
                null,
                null
        );

        List<ErroValidacaoLinhaDTO> erros = validator.validar(row);

        assertThat(erros).isNotEmpty();
        assertThat(erros).allMatch(e -> e.severidade() == ImportInconsistencySeverity.IMPEDITIVO);
        assertThat(erros).extracting(ErroValidacaoLinhaDTO::campo)
                .contains("matricula", "storeCode", "positionCode", "admissDate");
    }

    @Test
    @DisplayName("Data de demissão anterior à admissão deve gerar erro IMPEDITIVO")
    void demissaoAnteriorAdmissao_geraImpeditivo() {
        HrFileRow row = new HrFileRow(
                LocalDate.of(2023, 5, 1),
                10,
                "Marca Alpha",
                20,
                "Loja Centro",
                "M1002",
                LocalDate.of(2023, 5, 20),
                LocalDate.of(2023, 5, 10),
                30,
                "Consultor"
        );

        List<ErroValidacaoLinhaDTO> erros = validator.validar(row);

        assertThat(erros).hasSize(1);
        ErroValidacaoLinhaDTO erro = erros.get(0);
        assertThat(erro.campo()).isEqualTo("demissDate");
        assertThat(erro.severidade()).isEqualTo(ImportInconsistencySeverity.IMPEDITIVO);
        assertThat(erro.motivo()).contains("anterior");
    }
}
