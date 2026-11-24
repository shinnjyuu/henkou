package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.Audit;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class AuditCoverageVerifier {
    private final List<RequestMappingHandlerMapping> mappings;
    private final AuditControllerScope scope;
    private final List<ExternalHttpEndpoint> externalEndpoints;

    AuditCoverageVerifier(
            List<RequestMappingHandlerMapping> mappings,
            AuditControllerScope scope,
            List<ExternalHttpEndpoint> externalEndpoints) {
        this.mappings = mappings;
        this.scope = scope;
        this.externalEndpoints = externalEndpoints;
    }

    /** Fails once with every undeclared in-scope HTTP mapping in the current application context. */
    public void verify() {
        var issues = new TreeSet<String>();
        for (RequestMappingHandlerMapping mapping : mappings) {
            mapping.getHandlerMethods().forEach((route, handler) -> {
                if (scope.includes(handler) && handler.getMethodAnnotation(Audit.class) == null) {
                    addMissingPolicies(issues, route, handler);
                }
            });
        }
        verifyExternalEndpoints(issues);
        if (!issues.isEmpty()) {
            throw new IllegalStateException("HTTP audit policy verification failed:\n - "
                    + String.join("\n - ", issues));
        }
    }

    private static void addMissingPolicies(Set<String> issues, RequestMappingInfo route, HandlerMethod handler) {
        var methods = route.getMethodsCondition().getMethods().stream().map(RequestMethod::name).toList();
        if (methods.isEmpty()) {
            methods = List.of("ANY");
        }
        String declaration = ClassUtils.getUserClass(handler.getBeanType()).getName() + "#" + handler.getMethod().getName();
        for (String path : route.getPatternValues()) {
            for (String method : methods) {
                issues.add(method + " " + (path.isEmpty() ? "/" : path) + " -> " + declaration
                        + " is missing @Audit(RECORD) or @Audit(SKIP)");
            }
        }
    }

    private void verifyExternalEndpoints(Set<String> issues) {
        var registered = new HashSet<String>();
        for (ExternalHttpEndpoint endpoint : externalEndpoints) {
            String route = endpoint.method().name() + " " + endpoint.path();
            if (!registered.add(route)) {
                issues.add(route + " has multiple external audit declarations");
            }
            if (endpoint.policy() == null) {
                issues.add(route + " is missing an external audit policy (RECORD or SKIP)");
            }
        }
    }
}
