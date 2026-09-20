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
        String[] slices = valor.toLowerCase().split("-");

        Month month = MESES.get(slices[0]);
        int year = 2000 + Integer.parseInt(slices[1]);

        return LocalDate.of(year, month, 1);
    }
}
