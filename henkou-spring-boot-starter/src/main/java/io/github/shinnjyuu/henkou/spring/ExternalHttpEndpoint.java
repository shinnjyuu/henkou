package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.AuditContext;
import io.github.shinnjyuu.henkou.AuditPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;

import java.util.Objects;

/** An application-relative, literal HTTP path handled outside Spring MVC. */
public record ExternalHttpEndpoint(HttpMethod method, String path, AuditPolicy policy) {
    public ExternalHttpEndpoint {
        Objects.requireNonNull(method, "method");
        if (path == null || !path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("External endpoint path must be application-relative and literal");
        }
    }

    public boolean matches(HttpServletRequest request) {
        return method.name().equals(request.getMethod())
                && path.equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    /** Resolves attribution in the filter once the request principal is available. */
    public AuditContext context(HttpServletRequest request) {
        if (!matches(request)) {
            throw new IllegalArgumentException("Request does not match " + method + " " + path);
        }
        if (policy == null) {
            throw new IllegalStateException("Missing audit policy for " + method + " " + path);
        }
        return ServletAuditContext.create(request, policy, path);
    }
}
