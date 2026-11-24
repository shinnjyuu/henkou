CREATE TABLE example_role (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE example_role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_code VARCHAR(16) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    FOREIGN KEY (role_id) REFERENCES example_role (id) ON DELETE CASCADE
);
