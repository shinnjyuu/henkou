package io.github.shinnjyuu.henkou.spring;

import io.github.shinnjyuu.henkou.AuditRecord;
import io.github.shinnjyuu.henkou.AuditStore;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

final class JdbcAuditStore implements AuditStore {
    private static final String INSERT = """
            INSERT INTO henkou_audit_log
                (id, occurred_at_epoch_ms, actor, http_method, route_template,
                 operation, object_type, object_id, action, changes_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    JdbcAuditStore(DataSource dataSource) {
        this.dataSource = dataSource instanceof TransactionAwareDataSourceProxy proxy
                ? proxy.getTargetDataSource() : dataSource;
        this.jdbc = new JdbcTemplate(this.dataSource);
    }

    @Override
    public void append(AuditRecord record) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalTransactionStateException("The henkou JDBC DataSource must join the business transaction");
        }
        var values = new ArgumentPreparedStatementSetter(new Object[] {
                record.id().toString(), record.occurredAt().toEpochMilli(), record.actor(),
                record.httpMethod(), record.routeTemplate(), record.operation().name(), record.objectType(),
                record.objectId(), record.action(), json.writeValueAsString(record.fieldChanges())
        });
        jdbc.update(INSERT, statement -> {
            if (statement.getConnection().getAutoCommit()) {
                throw new IllegalTransactionStateException("The henkou JDBC connection is in auto-commit mode");
            }
            values.setValues(statement);
        });
    }
}
