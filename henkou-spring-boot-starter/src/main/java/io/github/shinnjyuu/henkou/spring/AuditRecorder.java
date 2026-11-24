package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.AuditChange;
import io.github.shinnjyuu.henkou.AuditContext;
import io.github.shinnjyuu.henkou.AuditPolicy;
import io.github.shinnjyuu.henkou.AuditRecord;
import io.github.shinnjyuu.henkou.AuditStore;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class AuditRecorder {
    private final AuditStore store;
    private final TransactionTemplate transaction;
    private final Clock clock;

    AuditRecorder(AuditStore store, PlatformTransactionManager transactionManager, Clock clock) {
        this.store = store;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        this.clock = clock;
    }

    /** Records the change under the current MVC handler's audit policy. */
    public void record(AuditChange change) {
        record(change, ServletAuditContext.current());
    }

    /** Records a change from an explicitly supplied HTTP audit context. */
    public void record(AuditChange change, AuditContext context) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(context, "context");
        if (context.policy() == AuditPolicy.SKIP) {
            return;
        }

        transaction.executeWithoutResult(status -> {
            try {
                appendChange(change, context);
            } catch (RuntimeException | Error failure) {
                status.setRollbackOnly();
                throw failure;
            }
        });
    }

    private void appendChange(AuditChange change, AuditContext context) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Auditing a change requires a write transaction");
        }
        var differences = change.fieldChanges();
        if (change.operation() == AuditChange.Operation.UPDATE && differences.isEmpty()) {
            return;
        }
        store.append(new AuditRecord(UUID.randomUUID(), clock.instant(), context.actor(),
                context.httpMethod(), context.routeTemplate(), change.operation(), change.objectType(),
                change.objectId(), change.action(), differences));
    }
}
