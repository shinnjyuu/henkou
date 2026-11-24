package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.Audit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

final class AuditMvcInterceptor implements HandlerInterceptor {
    private final AuditControllerScope scope;

    AuditMvcInterceptor(AuditControllerScope scope) {
        this.scope = scope;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.removeAttribute(ServletAuditContext.ATTRIBUTE);
        if (handler instanceof HandlerMethod method && scope.includes(method)) {
            Audit audit = method.getMethodAnnotation(Audit.class);
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (audit != null && pattern != null) {
                String routeTemplate = pattern.toString();
                request.setAttribute(ServletAuditContext.ATTRIBUTE,
                        ServletAuditContext.create(request, audit.value(), routeTemplate.isEmpty() ? "/" : routeTemplate));
            }
        }
        return true;
    }
}
