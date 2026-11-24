package io.github.shinnjyuu.henkou.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("henkou.mvc")
record AuditMvcProperties(List<String> controllerPackages) {
    AuditMvcProperties {
        controllerPackages = controllerPackages == null ? List.of() : List.copyOf(controllerPackages);
        if (controllerPackages.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("henkou.mvc.controller-packages must contain nonblank package names");
        }
    }
}
