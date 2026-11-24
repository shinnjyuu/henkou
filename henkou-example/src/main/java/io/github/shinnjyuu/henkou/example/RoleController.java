package io.github.shinnjyuu.henkou.example;

import io.github.shinnjyuu.henkou.Audit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static io.github.shinnjyuu.henkou.AuditPolicy.RECORD;
import static io.github.shinnjyuu.henkou.AuditPolicy.SKIP;

@RestController
@RequestMapping("/roles")
class RoleController {
    private final RoleService roles;

    RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping
    @Audit(SKIP)
    List<RoleService.Role> list() {
        return roles.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Audit(RECORD)
    RoleService.Role create(@RequestBody CreateRole request) {
        return roles.create(request.name(), request.permissions());
    }

    @PutMapping("/{id}/permissions")
    @Audit(RECORD)
    RoleService.Role updatePermissions(@PathVariable String id, @RequestBody AssignPermissions request) {
        return roles.updatePermissions(id, request.permissions());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Audit(RECORD)
    void delete(@PathVariable String id) {
        roles.delete(id);
    }

    record CreateRole(String name, List<RoleService.Permission> permissions) {
    }

    record AssignPermissions(List<RoleService.Permission> permissions) {
    }
}
