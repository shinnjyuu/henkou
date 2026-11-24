package io.github.shinnjyuu.henkou;

@FunctionalInterface
public interface AuditStore {
    /** Appends a record within the caller's business transaction. */
    void append(AuditRecord record);
}
