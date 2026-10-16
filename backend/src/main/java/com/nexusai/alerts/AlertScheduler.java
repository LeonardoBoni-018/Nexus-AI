package com.nexusai.alerts;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reavalia alertas de todas as organizacoes a cada 15 minutos; falha de uma nao impede as demais. */
@Component
public class AlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertService alerts;

    public AlertScheduler(AlertService alerts) {
        this.alerts = alerts;
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void run() {
        for (UUID org : alerts.organizationIds()) {
            try {
                alerts.evaluate(org);
            } catch (RuntimeException e) {
                log.error("Falha ao avaliar alertas da organizacao {}", org, e);
            }
        }
    }
}
