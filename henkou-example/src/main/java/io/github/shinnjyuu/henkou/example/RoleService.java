package io.github.shinnjyuu.henkou.example;

import io.github.shinnjyuu.henkou.AuditChange;
import io.github.shinnjyuu.henkou.AuditSnapshot;
import io.github.shinnjyuu.henkou.spring.AuditRecorder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
class RoleService {
    private final JdbcTemplate jdbc;
    private final AuditRecorder audit;

    RoleService(JdbcTemplate jdbc, AuditRecorder audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Role> list() {
        var identities = jdbc.query("SELECT id, name FROM example_role ORDER BY name, id",
                (row, ignored) -> new RoleIdentity(row.getString("id"), row.getString("name")));
        return identities.stream().map(this::readPermissions).toList();
    }

    @Transactional
    public Role create(String name, List<Permission> permissions) {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name must contain 1 to 120 characters");
        }
        var selected = normalizePermissions(permissions);
        var identity = new RoleIdentity(UUID.randomUUID().toString(), name.strip());
        jdbc.update("INSERT INTO example_role (id, name) VALUES (?, ?)", identity.id(), identity.name());
        insertPermissions(identity.id(), selected);
        var saved = readPermissions(identity);
        audit.record(AuditChange.created("role", identity.id(), "create_role", snapshot(saved)));
        return saved;
    }

    @Transactional
    public Role updatePermissions(String id, List<Permission> permissions) {
        var selected = normalizePermissions(permissions);
        var identity = lockRole(id);
        var before = snapshot(readPermissions(identity));
        jdbc.update("DELETE FROM example_role_permission WHERE role_id = ?", id);
        insertPermissions(id, selected);
        var saved = readPermissions(identity);
        audit.record(AuditChange.updated("role", id, "assign_permissions", before, snapshot(saved)));
        return saved;
    }

    @Transactional
    public void delete(String id) {
        var before = snapshot(readPermissions(lockRole(id)));
        jdbc.update("DELETE FROM example_role WHERE id = ?", id);
        audit.record(AuditChange.deleted("role", id, "delete_role", before));
    }

    private RoleIdentity lockRole(String id) {
        return jdbc.query("SELECT id, name FROM example_role WHERE id = ? FOR UPDATE",
                        (row, ignored) -> new RoleIdentity(row.getString("id"), row.getString("name")), id)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role does not exist"));
    }

    private Role readPermissions(RoleIdentity identity) {
        var permissions = jdbc.queryForList(
                        "SELECT permission_code FROM example_role_permission WHERE role_id = ?", String.class, identity.id())
                .stream().map(Permission::valueOf).sorted().toList();
        return new Role(identity.id(), identity.name(), permissions);
    }

    private void insertPermissions(String id, List<Permission> permissions) {
        if (!permissions.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO example_role_permission (role_id, permission_code) VALUES (?, ?)",
                    permissions.stream().map(permission -> new Object[] {id, permission.name()}).toList());
        }
    }

    private static List<Permission> normalizePermissions(List<Permission> permissions) {
        if (permissions == null || permissions.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permissions must be an array of permission codes");
        }
        return permissions.stream().distinct().sorted().toList();
    }

    private static AuditSnapshot snapshot(Role role) {
        return new AuditSnapshot(Map.of(
                "name", role.name(),
                "permissions", role.permissions().stream().map(Permission::name).toList()));
    }

    enum Permission {
        VIEW,
        EDIT
    }

    record Role(String id, String name, List<Permission> permissions) {
    }

    private record RoleIdentity(String id, String name) {
    }
}
