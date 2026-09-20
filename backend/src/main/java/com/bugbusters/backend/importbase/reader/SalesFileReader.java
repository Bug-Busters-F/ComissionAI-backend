package com.bugbusters.backend.importbase.reader;

import java.io.InputStream;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.SalesFileRow;

@Component
public class SalesFileReader implements FileReader<SalesFileRow> {
    public void read(InputStream input) {
    };
}
