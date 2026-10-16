package com.nexusai.common;

import com.nexusai.config.AppProperties;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Resolve periodos de consulta no fuso configurado, aplicando padrao (ultimos 30 dias) e limite maximo. */
@Component
public class PeriodResolver {

    private final AppProperties props;
    private final Clock clock;

    public PeriodResolver(AppProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(props.zone()));
    }

    public DateRange resolve(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.minusDays(29);
        return DateRange.of(start, end, props.maxQueryDays());
    }
}
