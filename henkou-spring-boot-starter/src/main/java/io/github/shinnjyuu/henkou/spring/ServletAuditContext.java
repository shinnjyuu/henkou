package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.AuditContext;
import io.github.shinnjyuu.henkou.AuditPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class ServletAuditContext {
    static final String ATTRIBUTE = ServletAuditContext.class.getName();

    private ServletAuditContext() {
    }

    static AuditContext create(HttpServletRequest request, AuditPolicy policy, String routeTemplate) {
        var principal = request.getUserPrincipal();
        String actor = principal == null ? "anonymous" : principal.getName();
        return new AuditContext(policy, actor, request.getMethod(), routeTemplate);
    }

    static AuditContext current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(ATTRIBUTE) instanceof AuditContext context) {
            return context;
        }
        throw new IllegalStateException("No HTTP audit context. Declare @Audit on the MVC handler "
                + "or pass the context of a registered external HTTP endpoint.");
    }
}
