CREATE TABLE henkou_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    occurred_at_epoch_ms BIGINT NOT NULL,
    actor VARCHAR(255) NOT NULL,
    http_method VARCHAR(32) NOT NULL,
    route_template VARCHAR(512) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    object_type VARCHAR(128) NOT NULL,
    object_id VARCHAR(255) NOT NULL,
    action VARCHAR(128) NOT NULL,
    changes_json LONGTEXT NOT NULL
);
