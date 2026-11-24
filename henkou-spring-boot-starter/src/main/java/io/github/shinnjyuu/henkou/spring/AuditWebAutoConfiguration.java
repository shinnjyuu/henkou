package io.github.shinnjyuu.henkou.spring;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AuditMvcProperties.class)
public class AuditWebAutoConfiguration {
    @Bean
    AuditControllerScope henkouControllerScope(BeanFactory beanFactory, AuditMvcProperties properties) {
        var packages = new ArrayList<>(properties.controllerPackages());
        if (AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        return new AuditControllerScope(List.copyOf(packages));
    }

    @Bean
    WebMvcConfigurer henkouMvcConfigurer(AuditControllerScope scope) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new AuditMvcInterceptor(scope));
            }
        };
    }

    @Bean
    AuditCoverageVerifier auditCoverageVerifier(
            ObjectProvider<RequestMappingHandlerMapping> mappings,
            AuditControllerScope scope,
            ObjectProvider<ExternalHttpEndpoint> endpoints) {
        return new AuditCoverageVerifier(mappings.orderedStream().toList(), scope, endpoints.orderedStream().toList());
    }
}
