package io.github.shinnjyuu.henkou;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public record AuditChange(
        Operation operation,
        String objectType,
        String objectId,
        String action,
        AuditSnapshot before,
        AuditSnapshot after) {

    private static final AuditSnapshot EMPTY = new AuditSnapshot(Map.of());

    public AuditChange {
        Objects.requireNonNull(operation, "operation");
        requireText(objectType, "objectType");
        requireText(objectId, "objectId");
        requireText(action, "action");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");

        if ((operation == Operation.CREATE || operation == Operation.EVENT) && !before.fields().isEmpty()) {
            throw new IllegalArgumentException(operation + " must have an empty before snapshot");
        }
        if ((operation == Operation.DELETE || operation == Operation.EVENT) && !after.fields().isEmpty()) {
            throw new IllegalArgumentException(operation + " must have an empty after snapshot");
        }
    }

    public static AuditChange created(String objectType, String objectId, String action, AuditSnapshot after) {
        return new AuditChange(Operation.CREATE, objectType, objectId, action, EMPTY, after);
    }

    public static AuditChange updated(
            String objectType, String objectId, String action, AuditSnapshot before, AuditSnapshot after) {
        return new AuditChange(Operation.UPDATE, objectType, objectId, action, before, after);
    }

    public static AuditChange deleted(String objectType, String objectId, String action, AuditSnapshot before) {
        return new AuditChange(Operation.DELETE, objectType, objectId, action, before, EMPTY);
    }

    public static AuditChange event(String objectType, String objectId, String action) {
        return new AuditChange(Operation.EVENT, objectType, objectId, action, EMPTY, EMPTY);
    }

    /** Compares values by top-level field, in field name order. */
    public List<AuditFieldChange> fieldChanges() {
        var names = new TreeSet<>(before.fields().keySet());
        names.addAll(after.fields().keySet());
        var changes = new ArrayList<AuditFieldChange>();
        for (String name : names) {
            boolean beforePresent = before.fields().containsKey(name);
            boolean afterPresent = after.fields().containsKey(name);
            Object previous = before.fields().get(name);
            Object current = after.fields().get(name);
            if (beforePresent != afterPresent || !Objects.equals(previous, current)) {
                changes.add(new AuditFieldChange(name, previous, current, beforePresent, afterPresent));
            }
        }
        return List.copyOf(changes);
    }

    private static void requireText(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
    }

    public enum Operation {
        CREATE,
        UPDATE,
        DELETE,
        EVENT
    }
}
