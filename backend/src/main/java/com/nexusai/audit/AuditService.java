package com.nexusai.audit;

import com.nexusai.common.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trilha de auditoria. Participa da transacao de quem chama, garantindo que a operacao e seu registro
 * sejam atomicos. Nunca receber segredos em {@code details}.
 */
@Service
public class AuditService {

    public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
        Page<AuditEvent> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

        Page<AuditEvent> findByOrganizationIdAndEventTypeOrderByCreatedAtDesc(UUID organizationId, String eventType,
                Pageable pageable);
    }

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID organizationId, UUID userId, String eventType, String entityType, Object entityId,
            String details) {
        repository.save(new AuditEvent(organizationId, userId, eventType, entityType,
                entityId == null ? null : entityId.toString(), details, MDC.get(RequestIdFilter.MDC_KEY),
                Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(UUID organizationId, String eventType, Pageable pageable) {
        if (eventType != null && !eventType.isBlank()) {
            return repository.findByOrganizationIdAndEventTypeOrderByCreatedAtDesc(organizationId, eventType, pageable);
        }
        return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }
}
