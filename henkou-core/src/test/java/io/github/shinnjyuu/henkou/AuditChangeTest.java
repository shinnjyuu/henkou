package io.github.shinnjyuu.henkou;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditChangeTest {
    @Test
    void recordsOnlyChangedFieldsInNameOrder() {
        var before = new AuditSnapshot(Map.of("permissions", List.of("VIEW"), "name", "Reviewer", "enabled", false));
        var after = new AuditSnapshot(Map.of("name", "Reviewer", "enabled", true, "permissions", List.of("VIEW", "EDIT")));

        var changes = AuditChange.updated("role", "42", "assign_permissions", before, after).fieldChanges();
        assertEquals(List.of(
                new AuditFieldChange("enabled", false, true, true, true),
                new AuditFieldChange("permissions", List.of("VIEW"), List.of("VIEW", "EDIT"), true, true)), changes);
        assertTrue(AuditChange.updated("role", "42", "save_role", after, after).fieldChanges().isEmpty());
    }

    @Test
    void distinguishesNullFromAnAbsentValue() {
        var fields = new HashMap<String, Object>();
        fields.put("note", null);
        var before = new AuditSnapshot(fields);
        var after = new AuditSnapshot(Map.of());

        var changes = AuditChange.updated("role", "42", "clear_note", before, after).fieldChanges();
        assertEquals(List.of(new AuditFieldChange("note", null, null, true, false)), changes);
    }

    @Test
    void recordsOnlyNewValuesForCreateAndOldValuesForDelete() {
        var fields = new AuditSnapshot(Map.of("name", "Reviewer"));
        assertEquals(List.of(new AuditFieldChange("name", null, "Reviewer", false, true)),
                AuditChange.created("role", "42", "create_role", fields).fieldChanges());
        assertEquals(List.of(new AuditFieldChange("name", "Reviewer", null, true, false)),
                AuditChange.deleted("role", "42", "delete_role", fields).fieldChanges());
        assertTrue(AuditChange.event("account", "42", "change_password").fieldChanges().isEmpty());
    }

    @Test
    void snapshotsCopyNestedValuesAndRejectArbitraryObjects() {
        var permissions = new ArrayList<>(List.of("VIEW"));
        var snapshot = new AuditSnapshot(Map.of("role", Map.of("permissions", permissions)));
        permissions.add("EDIT");

        assertEquals(Map.of("role", Map.of("permissions", List.of("VIEW"))), snapshot.fields());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.fields().clear());
        assertThrows(IllegalArgumentException.class, () -> new AuditSnapshot(Map.of("entity", new Object())));
    }
}
