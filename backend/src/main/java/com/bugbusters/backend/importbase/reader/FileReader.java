package com.bugbusters.backend.importbase.reader;

import java.io.InputStream;
import java.util.List;


public interface FileReader<T> {
    List<T> read(InputStream input);
}
