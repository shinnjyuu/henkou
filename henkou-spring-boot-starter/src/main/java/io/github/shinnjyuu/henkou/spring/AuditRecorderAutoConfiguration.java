package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.AuditStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@AutoConfiguration(after = AuditStoreAutoConfiguration.class,
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration")
@ConditionalOnSingleCandidate(PlatformTransactionManager.class)
public class AuditRecorderAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditRecorder.class)
    @ConditionalOnSingleCandidate(AuditStore.class)
    AuditRecorder auditRecorder(AuditStore store, PlatformTransactionManager transactions) {
        return new AuditRecorder(store, transactions, Clock.systemUTC());
    }
}
