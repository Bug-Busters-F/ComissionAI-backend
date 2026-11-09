package com.bugbusters.backend.dto.programapython;

import com.bugbusters.backend.model.CampanhaProgramaPython;
import com.bugbusters.backend.model.StatusAprovacaoPrograma;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "Representação versionada do programa Python executável e sua auditoria técnica")
public record ProgramaPythonResponse(
        @Schema(description = "Identificador único do programa Python")
        UUID id,

        @Schema(description = "Identificador da campanha vinculada")
        Long campanhaId,

        @Schema(description = "Número da versão da campanha")
        Integer versaoCampanhaNumero,

        @Schema(description = "Número sequencial da versão do código Python dentro da campanha")
        Integer versaoPrograma,

        @Schema(description = "Código-fonte Python completo executável")
        String codigoFonte,

        @Schema(description = "Checksum criptográfico SHA-256 do código-fonte para verificação de integridade")
        String checksumSha256,

        @Schema(description = "Status de homologação técnica")
        StatusAprovacaoPrograma statusAprovacao,

        @Schema(description = "Identificação do revisor que aprovou o código")
        String aprovadoPor,

        @Schema(description = "Data e hora da aprovação técnica")
        OffsetDateTime aprovadoEm,

        @Schema(description = "Identificação do revisor que rejeitou o código")
        String rejeitadoPor,

        @Schema(description = "Data e hora da rejeição técnica")
        OffsetDateTime rejeitadoEm,

        @Schema(description = "Justificativa, ressalvas ou motivo de recusa técnica")
        String justificativa,

        @Schema(description = "Data e hora de criação desta versão do programa")
        OffsetDateTime criadoEm,

        @Schema(description = "Data e hora da última modificação")
        OffsetDateTime atualizadoEm,

        @Schema(description = "Indica se o programa está formalmente aprovado e apto para simulação/fechamento")
        boolean validoParaExecucao
) {
    public static ProgramaPythonResponse de(CampanhaProgramaPython entidade) {
        if (entidade == null) {
            return null;
        }
        boolean hashIntegro = Objects.equals(
                entidade.getChecksumSha256(),
                CampanhaProgramaPython.calcularChecksum(entidade.getCodigoFonte())
        );
        boolean valido = entidade.isAprovado() && hashIntegro;

        Long cId = entidade.getCampanhaVersao() != null && entidade.getCampanhaVersao().getCampanha() != null
                ? entidade.getCampanhaVersao().getCampanha().getId() : null;
        Integer num = entidade.getCampanhaVersao() != null
                ? entidade.getCampanhaVersao().getNumero() : null;

        return new ProgramaPythonResponse(
                entidade.getId(),
                cId,
                num,
                entidade.getVersaoPrograma(),
                entidade.getCodigoFonte(),
                entidade.getChecksumSha256(),
                entidade.getStatusAprovacao(),
                entidade.getAprovadoPor(),
                entidade.getAprovadoEm(),
                entidade.getRejeitadoPor(),
                entidade.getRejeitadoEm(),
                entidade.getJustificativa(),
                entidade.getCriadoEm(),
                entidade.getAtualizadoEm(),
                valido
        );
    }
}

