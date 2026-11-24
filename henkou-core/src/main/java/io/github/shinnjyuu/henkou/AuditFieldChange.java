package io.github.shinnjyuu.henkou;

/** Presence flags distinguish a missing field from one whose value is null. */
public record AuditFieldChange(
        String field,
        Object before,
        Object after,
        boolean beforePresent,
        boolean afterPresent) {
}
