package com.nexusai.common;

import com.nexusai.common.exception.ApiException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Periodo fechado [from, to] em datas locais (fuso America/Sao_Paulo), ambos inclusivos.
 * O periodo anterior tem exatamente a mesma quantidade de dias e termina no dia anterior a {@code from}.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static DateRange of(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null) {
            throw ApiException.badRequest("Informe as datas 'from' e 'to'");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("'to' nao pode ser anterior a 'from'");
        }
        DateRange r = new DateRange(from, to);
        if (r.days() > maxDays) {
            throw ApiException.badRequest("Periodo maximo permitido: " + maxDays + " dias");
        }
        return r;
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public DateRange previous() {
        long d = days();
        return new DateRange(from.minusDays(d), from.minusDays(1));
    }
}
