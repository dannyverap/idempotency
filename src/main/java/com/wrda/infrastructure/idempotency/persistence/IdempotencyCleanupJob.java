package com.wrda.infrastructure.idempotency.persistence;

import com.wrda.application.idempotency.IdempotencyService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IdempotencyCleanupJob {

    private static final Logger LOG = Logger.getLogger(IdempotencyCleanupJob.class);
    private final IdempotencyService service;

    @Inject
    public IdempotencyCleanupJob(IdempotencyService service) {
        this.service = service;
    }

    @Scheduled(every = "{wrda.idempotency.cleanup-every}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void purgeExpired() {
        long deleted = service.purgeExpired();
        if (deleted > 0) {
            LOG.infof("Idempotencia: %d keys vencidas eliminadas", deleted);
        }
    }
}
