package com.bugbusters.backend.importbase;

import org.springframework.stereotype.Service;

// import java.time.OffsetDateTime;
// import java.util.List;

@Service 
public class ImportService {
    public ImportResponse processImport(ImportRequest request) {
        return new ImportResponse(
                request.getFile().getOriginalFilename(),
                request.getImportType(),
                "PROCESSADO_COM_AVISOS",
                100,
                99,
                false
                // OffsetDateTime.now()
                // List.of(new ItemInconsistenciaDTO(12, "canal", "Canal não preenchido; atribuído canal padrão.", ImportInconsistencySeverity.AVISO)),
        );
    }
}
