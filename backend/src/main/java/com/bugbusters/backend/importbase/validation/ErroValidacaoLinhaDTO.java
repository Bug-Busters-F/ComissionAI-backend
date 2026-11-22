package com.bugbusters.backend.importbase.validation;

import com.bugbusters.backend.importbase.ImportInconsistencySeverity;

public record ErroValidacaoLinhaDTO(
        String campo,
        String motivo,
        ImportInconsistencySeverity severidade
) {
    public static ErroValidacaoLinhaDTO impeditivo(String campo, String motivo) {
        return new ErroValidacaoLinhaDTO(campo, motivo, ImportInconsistencySeverity.IMPEDITIVO);
    }

    public static ErroValidacaoLinhaDTO advertencia(String campo, String motivo) {
        return new ErroValidacaoLinhaDTO(campo, motivo, ImportInconsistencySeverity.ADVERTENCIA);
    }

    public static ErroValidacaoLinhaDTO aviso(String campo, String motivo) {
        return new ErroValidacaoLinhaDTO(campo, motivo, ImportInconsistencySeverity.AVISO);
    }
}
