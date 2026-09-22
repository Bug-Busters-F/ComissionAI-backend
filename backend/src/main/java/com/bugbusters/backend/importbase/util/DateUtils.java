package com.bugbusters.backend.importbase.util;

import java.time.LocalDate;
import java.time.Month;
import java.util.Map;

public class DateUtils {
    private static final Map<String, Month> MESES = Map.ofEntries(
            Map.entry("jan", Month.JANUARY),
            Map.entry("fev", Month.FEBRUARY),
            Map.entry("mar", Month.MARCH),
            Map.entry("abr", Month.APRIL),
            Map.entry("mai", Month.MAY),
            Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY),
            Map.entry("ago", Month.AUGUST),
            Map.entry("set", Month.SEPTEMBER),
            Map.entry("out", Month.OCTOBER),
            Map.entry("nov", Month.NOVEMBER),
            Map.entry("dez", Month.DECEMBER));

    public static LocalDate parseMonthYear(String valor) {
    if (valor == null || valor.isBlank()) {
        throw new IllegalArgumentException("Data de referência vazia");
    }

    String[] slices = valor.trim().toLowerCase().split("-");
    if (slices.length != 2) {
        throw new IllegalArgumentException("Formato de data inválido, esperado 'mmm-aa': " + valor);
    }

    Month month = MESES.get(slices[0]);
    if (month == null) {
        throw new IllegalArgumentException("Mês não reconhecido: " + slices[0]);
    }

    int year;
    try {
        year = 2000 + Integer.parseInt(slices[1]);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Ano inválido: " + slices[1]);
    }

    return LocalDate.of(year, month, 1);
}

    public static LocalDate parseDate(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        valor = valor.trim();
        if (valor.matches("(?i)^[a-z]{3}-\\d{2}$")) {
            return parseMonthYear(valor);
        }
        for (String pattern : java.util.List.of("M/d/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy")) {
            try {
                return LocalDate.parse(valor, java.time.format.DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }
        return parseMonthYear(valor);
    }
}
