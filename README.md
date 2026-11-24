# henkou

[简体中文](#简体中文) · [English](#english)

## 简体中文

henkou 是一个 Java 数据变更审计框架，让每个业务入口明确声明是否留痕，在构建时发现遗漏，并为需要审计的变更记录字段前后值。

### 运行示例

使用 JDK 25。示例默认用 H2 运行，Maven Wrapper 会执行单元测试和集成测试。

```sh
sh ./mvnw verify
java -jar henkou-example/target/henkou-example-0.1.0-SNAPSHOT-exec.jar
```

Windows 可用 `.\mvnw.cmd verify`。示例启动后，创建一个角色，再将返回的 `id` 填到更新路径中。

```sh
curl -X POST http://localhost:8080/roles \
  -H 'Content-Type: application/json' \
  -d '{"name":"Reviewer","permissions":["VIEW"]}'

curl -X PUT http://localhost:8080/roles/ROLE_ID/permissions \
  -H 'Content-Type: application/json' \
  -d '{"permissions":["VIEW","EDIT"]}'
```

第二次请求在 `henkou_audit_log` 生成一条 `UPDATE` 记录。它的 `changes_json` 只包含发生变化的 `permissions` 字段。

```json
[{"field":"permissions","before":["VIEW"],"after":["VIEW","EDIT"],"beforePresent":true,"afterPresent":true}]
```

### 接入 Spring Boot 应用

#### 引入 Starter 并创建审计表

在 henkou 根目录运行 `sh ./mvnw -pl henkou-spring-boot-starter -am install`，然后在使用 Spring Boot 4.2、Spring MVC 和 JDBC 的应用中加入依赖。

```xml
<dependency>
    <groupId>io.github.shinnjyuu</groupId>
    <artifactId>henkou-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

将 [建表 SQL](henkou-spring-boot-starter/src/main/resources/META-INF/henkou/db/migration/V1__henkou_audit_log.sql) 纳入应用的数据库迁移，并为它分配应用中的迁移版本。默认 JDBC 存储使用应用的 `DataSource`，并要求该连接已加入业务事务。应用可通过提供 `AuditStore` Bean 替换存储实现，替换实现也应使用当前业务事务。

#### 声明每个入口的策略

```java
import io.github.shinnjyuu.henkou.Audit;
import static io.github.shinnjyuu.henkou.AuditPolicy.RECORD;
import static io.github.shinnjyuu.henkou.AuditPolicy.SKIP;

@GetMapping("/roles")
@Audit(SKIP)
List<Role> listRoles() {
    return roles.list();
}

@PutMapping("/roles/{id}/permissions")
@Audit(RECORD)
Role assignPermissions(@PathVariable String id, @RequestBody AssignPermissions request) {
    return roles.updatePermissions(id, request.permissions());
}
```

Starter 默认检查 Spring Boot 应用主包及其子包中实际注册的 Controller。使用 `henkou.mvc.controller-packages` 列出额外纳管的包。过滤器入口可以声明一个 `ExternalHttpEndpoint` Bean，在注册过滤器时复用 `path()`，在匹配请求时复用 `matches(request)`。

```java
@Bean
ExternalHttpEndpoint logoutEndpoint() {
    return new ExternalHttpEndpoint(HttpMethod.POST, "/session/logout", SKIP);
}
```

#### 在写入事务中记录变化

将允许留痕的字段显式投影为 `AuditSnapshot`。快照接受 JSON 标量、列表和字符串键的映射。权限等无序集合应先按业务规则规范排序。改密等操作可用 `AuditChange.event(...)` 记录事件。

```java
@Transactional
public Role updatePermissions(String id, List<Permission> permissions) {
    var role = lockRole(id);
    var before = snapshot(readPermissions(role));
    replacePermissions(id, permissions);
    var saved = readPermissions(role);
    audit.record(AuditChange.updated("role", id, "assign_permissions", before, snapshot(saved)));
    return saved;
}
```

`audit` 是 Starter 提供的 `AuditRecorder`。MVC 请求自动提供策略、HTTP 方法、路径模板和认证主体。匿名操作记为 `anonymous`，时间按 UTC 的 Unix 毫秒存储。过滤器可以在业务写入事务内调用 `audit.record(change, endpoint.context(request))`。`RECORD` 的审计写入失败会使业务事务回滚，`SKIP` 和字段未变化的更新不会写入审计表。

#### 在构建中检查声明

在接入应用中加入 `*IT` 测试，并在 Maven Failsafe 中绑定 `integration-test` 和 `verify`。可以复用 [示例的插件配置](henkou-example/pom.xml)。

```java
@SpringBootTest
class AuditCoverageIT {
    @Autowired
    private AuditCoverageVerifier verifier;

    @Test
    void verifiesEveryAuditDeclaration() {
        verifier.verify();
    }
}
```

运行 `sh ./mvnw verify`。遗漏声明时，报告会列出对应的 HTTP 方法、路径和 Controller 方法。为不同 Spring 配置注册的路由，应在各自的配置下运行测试。构建检查验证路由声明覆盖，业务集成测试验证对应写入路径在提交后产生预期的字段变更记录。

### 使用 MySQL 8.4 验证

在 MySQL 8.4 中准备一个空的测试数据库和具备迁移权限的测试用户。设置 `HENKOU_MYSQL_URL`、`HENKOU_MYSQL_USER` 和 `HENKOU_MYSQL_PASSWORD`，运行 `sh ./mvnw -Pmysql verify`。集成测试会清理示例业务表和审计表中的数据。CI 为 H2 和 MySQL 8.4 各运行一遍相同的验收。

### 许可证

本项目采用 [MIT 许可证](LICENSE)。

---

## English

henkou is a Java framework for data change auditing. It requires an explicit audit policy for every in-scope entry point, flags missing declarations at build time, and records before-and-after values for audited fields.

### Run the example

Use JDK 25. The example uses H2 by default. The Maven Wrapper runs the unit and integration tests.

```sh
sh ./mvnw verify
java -jar henkou-example/target/henkou-example-0.1.0-SNAPSHOT-exec.jar
```

On Windows, use `.\mvnw.cmd verify`. Once the example is running, create a role and use the returned `id` in the update URL.

```sh
curl -X POST http://localhost:8080/roles \
  -H 'Content-Type: application/json' \
  -d '{"name":"Reviewer","permissions":["VIEW"]}'

curl -X PUT http://localhost:8080/roles/ROLE_ID/permissions \
  -H 'Content-Type: application/json' \
  -d '{"permissions":["VIEW","EDIT"]}'
```

The second request writes an `UPDATE` entry to `henkou_audit_log`. Its `changes_json` contains just the changed `permissions` field.

```json
[{"field":"permissions","before":["VIEW"],"after":["VIEW","EDIT"],"beforePresent":true,"afterPresent":true}]
```

### Integrate with a Spring Boot application

#### Add the Starter and create the audit table

Run `sh ./mvnw -pl henkou-spring-boot-starter -am install` from the henkou root, then add the dependency to an application that uses Spring Boot 4.2, Spring MVC, and JDBC.

```xml
<dependency>
    <groupId>io.github.shinnjyuu</groupId>
    <artifactId>henkou-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Add the audit table SQL to your application's database migrations and give it a migration version in your application's sequence. The default JDBC store uses the application's `DataSource` and requires the connection to participate in the business transaction. You can supply an `AuditStore` bean to use a different store. That store should participate in the same transaction. [Audit table migration](henkou-spring-boot-starter/src/main/resources/META-INF/henkou/db/migration/V1__henkou_audit_log.sql)

#### Declare an audit policy for each endpoint

```java
import io.github.shinnjyuu.henkou.Audit;
import static io.github.shinnjyuu.henkou.AuditPolicy.RECORD;
import static io.github.shinnjyuu.henkou.AuditPolicy.SKIP;

@GetMapping("/roles")
@Audit(SKIP)
List<Role> listRoles() {
    return roles.list();
}

@PutMapping("/roles/{id}/permissions")
@Audit(RECORD)
Role assignPermissions(@PathVariable String id, @RequestBody AssignPermissions request) {
    return roles.updatePermissions(id, request.permissions());
}
```

By default, the Starter checks registered controllers in the Spring Boot application's base package and its subpackages. List additional packages in `henkou.mvc.controller-packages`. For an endpoint handled by a filter, declare an `ExternalHttpEndpoint` bean. Reuse `path()` when registering the filter and `matches(request)` when matching requests.

```java
@Bean
ExternalHttpEndpoint logoutEndpoint() {
    return new ExternalHttpEndpoint(HttpMethod.POST, "/session/logout", SKIP);
}
```

#### Record changes within the write transaction

Build an `AuditSnapshot` from the fields you want to audit. Snapshots accept JSON scalars, lists, and maps with string keys. Sort unordered values such as permission sets consistently before capturing them. For operations such as password changes, use `AuditChange.event(...)` to record the event.

```java
@Transactional
public Role updatePermissions(String id, List<Permission> permissions) {
    var role = lockRole(id);
    var before = snapshot(readPermissions(role));
    replacePermissions(id, permissions);
    var saved = readPermissions(role);
    audit.record(AuditChange.updated("role", id, "assign_permissions", before, snapshot(saved)));
    return saved;
}
```

`audit` is the `AuditRecorder` configured by the Starter. An MVC request supplies the policy, HTTP method, route template, and authenticated principal. Anonymous requests are recorded as `anonymous`, and timestamps are stored as Unix epoch milliseconds. A filter can call `audit.record(change, endpoint.context(request))` inside the business write transaction. If an audit write fails under `RECORD`, the business transaction rolls back. The recorder skips audit inserts under `SKIP` and when an update leaves the audited fields unchanged.

#### Check audit declarations during the build

Add an `*IT` test to your application and bind the Maven Failsafe goals `integration-test` and `verify`. [Example Maven configuration](henkou-example/pom.xml)

```java
@SpringBootTest
class AuditCoverageIT {
    @Autowired
    private AuditCoverageVerifier verifier;

    @Test
    void verifiesEveryAuditDeclaration() {
        verifier.verify();
    }
}
```

Run `sh ./mvnw verify`. The report lists the HTTP method, path, and controller method for each missing declaration. Run the check under each Spring configuration that registers different routes. The build check verifies route coverage, and integration tests verify that the corresponding writes produce the expected audit entries after commit.

### Verify with MySQL 8.4

Prepare an empty MySQL 8.4 test database and a test user with migration privileges. Set `HENKOU_MYSQL_URL`, `HENKOU_MYSQL_USER`, and `HENKOU_MYSQL_PASSWORD`, then run `sh ./mvnw -Pmysql verify`. The integration tests clear data from the example's business and audit tables. CI runs the same tests against H2 and MySQL 8.4.

### License

henkou is licensed under the [MIT License](LICENSE).
