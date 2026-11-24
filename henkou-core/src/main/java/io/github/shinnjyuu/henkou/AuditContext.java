package io.github.shinnjyuu.henkou;

import java.util.Objects;

/** Policy and attribution for one HTTP operation. */
public record AuditContext(AuditPolicy policy, String actor, String httpMethod, String routeTemplate) {
    public AuditContext {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(httpMethod, "httpMethod");
        Objects.requireNonNull(routeTemplate, "routeTemplate");
    }
}
