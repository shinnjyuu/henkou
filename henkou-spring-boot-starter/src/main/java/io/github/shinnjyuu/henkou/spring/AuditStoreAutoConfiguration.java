package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.AuditStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class AuditStoreAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditStore.class)
    @ConditionalOnSingleCandidate(DataSource.class)
    AuditStore henkouAuditStore(DataSource dataSource) {
        return new JdbcAuditStore(dataSource);
    }
}
