package com.bugbusters.backend.dto.preparacao;

public record ArquivoOriginalDownloadDTO(
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        byte[] conteudo
) {}
