package com.bugbusters.backend.importbase.reader;

import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;


public interface FileReader<T> {
    void forEach(InputStream input, Consumer<T> consumer);

    default List<T> read(InputStream input) {
        List<T> rows = new ArrayList<>();
        forEach(input, rows::add);
        return rows;
    }
}
