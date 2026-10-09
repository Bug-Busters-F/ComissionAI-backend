package com.bugbusters.backend.dto.regra;

import java.util.List;

public record ConsultaConcorrenciaResponse(
        int totalConcorrentes,
        boolean temEmpate,
        boolean temIdentica,
        List<RegraConcorrenteDTO> regrasConcorrentes
) {}
