package com.bugbusters.backend.importbase;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.bugbusters.backend.importbase.reader.FileReader;

@Service
public class ImportService {
    private FileReaderFactory readerFactory; 

    public ImportService(FileReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }

    public ImportResponse processImport(
    MultipartFile file,
    ImportType importType
    ) {
        FileReader<?> reader = readerFactory.getFileReader(importType);

        try (InputStream input = file.getInputStream()){
            List<?> dados = reader.read(input);

            return new ImportResponse(
                file.getOriginalFilename(),
                importType,
                dados
            );

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo", e);
        }
    }
}
