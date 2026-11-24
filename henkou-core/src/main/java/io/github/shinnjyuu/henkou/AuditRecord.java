package io.github.shinnjyuu.henkou;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditRecord(
        UUID id,
        Instant occurredAt,
        String actor,
        String httpMethod,
        String routeTemplate,
        AuditChange.Operation operation,
        String objectType,
        String objectId,
        String action,
        List<AuditFieldChange> fieldChanges) {

    public AuditRecord {
        fieldChanges = List.copyOf(fieldChanges);
    }
}
