package com.bugbusters.backend.importbase.reader;

import java.io.InputStream;
// import java.util.List;

import org.springframework.stereotype.Component;

import com.bugbusters.backend.importbase.dto.HrFileRow;

@Component
public class HrFileReader implements FileReader<HrFileRow> {
    public void read(InputStream input){
    };
}
