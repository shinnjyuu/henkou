package io.github.shinnjyuu.henkou.spring;

import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

record AuditControllerScope(List<String> packages) {
    boolean includes(HandlerMethod handler) {
        String controllerPackage = ClassUtils.getUserClass(handler.getBeanType()).getPackageName();
        return packages.stream().anyMatch(candidate ->
                controllerPackage.equals(candidate) || controllerPackage.startsWith(candidate + "."));
    }
}
