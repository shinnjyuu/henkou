package io.github.shinnjyuu.henkou.example;

import io.github.shinnjyuu.henkou.spring.ExternalHttpEndpoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static io.github.shinnjyuu.henkou.AuditPolicy.SKIP;

@Configuration(proxyBeanMethods = false)
class SessionFilterConfiguration {
    @Bean
    ExternalHttpEndpoint logoutEndpoint() {
        return new ExternalHttpEndpoint(HttpMethod.POST, "/session/logout", SKIP);
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> logoutFilter(ExternalHttpEndpoint logoutEndpoint) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                if (!logoutEndpoint.matches(request)) {
                    chain.doFilter(request, response);
                    return;
                }
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        });
        registration.addUrlPatterns(logoutEndpoint.path());
        return registration;
    }
}
