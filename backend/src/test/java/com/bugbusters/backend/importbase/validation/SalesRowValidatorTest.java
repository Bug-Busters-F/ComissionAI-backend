package com.bugbusters.backend.importbase.validation;

import com.bugbusters.backend.importbase.ImportInconsistencySeverity;
import com.bugbusters.backend.importbase.dto.SalesFileRow;
import com.bugbusters.backend.registration.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalesRowValidatorTest {

    private SalesRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SalesRowValidator();
    }

    @Test
    @DisplayName("Linha de vendas válida com colaborador cadastrado não deve gerar erros")
    void linhaValida_semErros() {
        SalesFileRow row = new SalesFileRow(
                LocalDate.of(2023, 5, 10),
                101,
                "Marca",
                201,
                "Loja",
                "M1001",
                new BigDecimal("1500.00")
        );

        Registration reg = new Registration();
        reg.setRegistration("M1001");
        Map<String, Registration> colaboradores = Map.of("M1001", reg);

        List<ErroValidacaoLinhaDTO> erros = validator.validar(
                row,
                YearMonth.of(2023, 5),
                colaboradores,
                null
        );

        assertThat(erros).isEmpty();
    }

    @Test
    @DisplayName("Campos obrigatórios ausentes ou nulos devem gerar IMPEDITIVO")
    void camposObrigatoriosAusentes_geraImpeditivo() {
        SalesFileRow row = new SalesFileRow(
                null,
                null,
                null,
                null,
                null,
                "",
                null
        );

        List<ErroValidacaoLinhaDTO> erros = validator.validar(
                row,
                YearMonth.of(2023, 5)
        );

        assertThat(erros).isNotEmpty();
        assertThat(erros).allMatch(e -> e.severidade() == ImportInconsistencySeverity.IMPEDITIVO);
        assertThat(erros).extracting(ErroValidacaoLinhaDTO::campo)
                .contains("referenceDate", "storeCode", "brandCode", "saleValue", "matricula");
    }

    @Test
    @DisplayName("Valor da venda menor ou igual a zero deve gerar IMPEDITIVO")
    void valorZeroOuNegativo_geraImpeditivo() {
        SalesFileRow row = new SalesFileRow(
                LocalDate.of(2023, 5, 15),
                101,
                "Marca",
                201,
                "Loja",
                "M1001",
                BigDecimal.ZERO
        );

        List<ErroValidacaoLinhaDTO> erros = validator.validar(
                row,
                YearMonth.of(2023, 5)
        );

        assertThat(erros).hasSize(1);
        ErroValidacaoLinhaDTO erro = erros.get(0);
        assertThat(erro.campo()).isEqualTo("saleValue");
        assertThat(erro.severidade()).isEqualTo(ImportInconsistencySeverity.IMPEDITIVO);
    }

    @Test
    @DisplayName("Matrícula ausente no cadastro de RH deve gerar ADVERTENCIA")
    void matriculaNaoCadastrada_geraAdvertencia() {
        SalesFileRow row = new SalesFileRow(
                LocalDate.of(2023, 5, 15),
                101,
                "Marca",
                201,
                "Loja",
                "M9999",
                new BigDecimal("250.00")
        );

        List<ErroValidacaoLinhaDTO> erros = validator.validar(
                row,
                YearMonth.of(2023, 5),
                new HashMap<>(),
                null
        );

        assertThat(erros).hasSize(1);
        ErroValidacaoLinhaDTO erro = erros.get(0);
        assertThat(erro.campo()).isEqualTo("matricula");
        assertThat(erro.severidade()).isEqualTo(ImportInconsistencySeverity.ADVERTENCIA);
        assertThat(erro.motivo()).contains("não cadastrada");
    }

    @Test
    @DisplayName("Data fora da competência informada deve gerar IMPEDITIVO")
    void dataForaCompetencia_geraImpeditivo() {
        SalesFileRow row = new SalesFileRow(
                LocalDate.of(2023, 6, 2),
                101,
                "Marca",
                201,
                "Loja",
                "M1001",
                new BigDecimal("300.00")
        );

        Registration reg = new Registration();
        reg.setRegistration("M1001");

        List<ErroValidacaoLinhaDTO> erros = validator.validar(
                row,
                YearMonth.of(2023, 5),
                Map.of("M1001", reg),
                null
        );

        assertThat(erros).hasSize(1);
        ErroValidacaoLinhaDTO erro = erros.get(0);
        assertThat(erro.campo()).isEqualTo("referenceDate");
        assertThat(erro.severidade()).isEqualTo(ImportInconsistencySeverity.IMPEDITIVO);
    }

    @Test
    @DisplayName("Data de venda anterior à admissão do colaborador deve gerar AVISO")
    void dataVendaAnteriorAdmissao_geraAviso() {
        SalesFileRow row = new SalesFileRow(
                LocalDate.of(2023, 5, 10),
                101,
                "Marca",
                201,
                "Loja",
                "M1001",
                new BigDecimal("300.00")
        );

        Registration reg = new Registration();
        reg.setRegistration("M1001");
        reg.setAdmissDate(LocalDate.of(2023, 5, 20));

        List<ErroValidacaoLinhaDTO> erros = validator.validar(
                row,
                YearMonth.of(2023, 5),
                Map.of("M1001", reg),
                null
        );

        assertThat(erros).hasSize(1);
        ErroValidacaoLinhaDTO erro = erros.get(0);
        assertThat(erro.campo()).isEqualTo("referenceDate");
        assertThat(erro.severidade()).isEqualTo(ImportInconsistencySeverity.AVISO);
        assertThat(erro.motivo()).contains("anterior");
    }
}
