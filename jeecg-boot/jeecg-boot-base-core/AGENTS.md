# AGENTS.md — jeecg-boot-base-core

> 本文件为 AI 编码助手（Claude Code、Codex、Cursor、Kimi Code 等）提供模块级指引。读者默认对本模块一无所知。上层仓库级说明见 `JeecgBoot/AGENTS.md` 与 `jeecg-boot/AGENTS.md`（本文件与之互补，冲突时以实际代码为准）。除非用户明确要求其他语言，请优先使用简体中文回答。

## 模块定位

`jeecg-boot-base-core` 是 JeecgBoot 后端（`jeecg-boot-parent` 3.9.6）的**核心框架模块**：一个被其他业务模块依赖的类库 jar，**没有可执行入口（无 main 类）**。它承载平台的横切基础设施：

- **鉴权**：Apache Shiro 3.0 + JWT（`ShiroConfig` / `ShiroRealm` / `JwtToken` / `JwtFilter`），Redis 会话（shiro-redis + Jedis/Lettuce）。
- **ORM 基础设施**：MyBatis-Plus 3.5.x（spring-boot4 starter）+ Druid + dynamic-datasource 多数据源；租户解析与数据权限拦截器（`config/mybatis/`）。
- **通用能力**：统一返回包装 `Result<T>`、查询构造器 `QueryGenerator`、Controller/Service/Entity 基类（`JeecgController` / `JeecgService` / `JeecgEntity`）、~40 个工具类。
- **AOP**：操作日志（`AutoLogAspect`）、字典翻译（`DictAspect`）、数据权限（`PermissionDataAspect`）。
- **安全防火墙**：SQL 注入拦截与字典表白名单、SSRF 防护（`common/util/security/`）、SVG 安全过滤、内部 API 守卫（`InnerApiGuardInterceptor`）。
- **集成**：Quartz、WebSocket、MinIO / 阿里 OSS、阿里/腾讯短信、JustAuth 第三方登录、AutoPoi Excel、minidao、代码生成器（`codegenerate`）、Knife4j / springdoc 接口文档、Freemarker 邮件模板。

关键约束：**全程使用 `jakarta` 命名空间（非 `javax`）**；Java 17；Spring Boot 4.1.0；日志用 SLF4J/Logback（pom 已排除 Shiro 带入的 log4j2）。

## 构建与测试

本模块不可独立启动，只能作为依赖被 `jeecg-system-biz` 等业务模块引用。在 `jeecg-boot/` 根目录执行：

```bash
mvn clean package -pl jeecg-boot-base-core -am        # 单独构建本模块（含依赖）
mvn test -DskipTests=false -pl jeecg-boot-base-core -Dtest=<TestClassName>   # 跑单个测试类
```

- 根 pom 默认 `<skipTests>true</skipTests>`，跑测试必须显式 `-DskipTests=false`。
- 私有构件仓库：`maven.aliyun.com` 与 `maven.jeecg.com`（minidao、codegenerate、autopoi 等私有构件由此拉取），依赖解析失败先检查仓库可达性。
- 产物：`target/jeecg-boot-base-core-3.9.6.jar`。
- 本模块自身没有 `application*.yml`；全部运行时配置在启动模块 `jeecg-module-system/jeecg-system-start` 中。

## 代码组织

源码根：`src/main/java/org/jeecg/`，三大包：

| 包 | 内容 |
|---|---|
| `common/` | `api`（`CommonAPI` 跨模块调用接口、`Result<T>` 统一返回）、`aspect`（日志/字典/数据权限 AOP）、`constant`、`desensitization`（脱敏）、`exception`（`JeecgBootException` 体系 + 全局处理器）、`system`（`base` 三层基类、`query/QueryGenerator`、`annotation`/`vo`）、`util`（`oConvertUtils`、`SqlInjectionUtil`、`IpUtils`、`Md5Util`、`MinioUtil`、`DateUtils` 等） |
| `config/` | Spring 配置类：`shiro/`（Shiro 全配置 + `JwtFilter` + `@IgnoreAuth` 注解）、`mybatis/`（拦截器、租户 `JeecgTenantParser`、`TenantContext`）、`filter/`（`TraceIdFilter`、`SvgSecurityFilter`、`WebsocketFilter`、`RequestBodyReserveFilter`）、`firewall/`（SQL 注入防护、内部 API 守卫、低代码模式拦截器）、`oss/`、`sign/`、`tencent/`、Druid、Swagger/Knife4j、WebSocket、CORS、Freemarker 邮件配置 |
| `modules/base/` | 跨模块公共持久化：`BaseCommonMapper`（+ `mapper/xml/BaseCommonMapper.xml`）与 `BaseCommonService(Impl)`，负责系统日志落库 |

资源：`src/main/resources/` 含 `META-INF/spring.factories`（注册 `DruidWallConfigRegister` 启动监听器）、`META-INF/spring/org.springframework.boot.SpringApplicationRunListeners`、Freemarker 邮件模板（`templates/email/*.ftl`）、`static/pca.json`（省市区数据）。

### 使用约定（其他模块都依赖这些，改动影响面大）

- **Controller 基类** `JeecgController<T, S extends IService<T>>`（`common/system/base/controller/JeecgController.java`）：自带 Excel 导入导出（`exportXls` / `importExcel`），service 以 `protected S service` 注入。
- **统一返回**：`Result.OK(data)` / `Result.OK(msg, data)` / `Result.error(msg)`，数据在 `result` 字段。
- **查询构造**：`QueryGenerator.initQueryWrapper(entity, request.getParameterMap())` 按 HTTP 参数自动组装 `QueryWrapper`，列表查询一律走它。
- **实体基类** `JeecgEntity` 提供通用字段（id、创建人/时间等）。
- 代码注释与文档以中文为主；代码中保留了大量 `update-begin---author:xxx---date:xxx for:【issues/xxxx】...update-end` 变更标记注释，改动相关代码时沿用同一风格，不要删除既有标记。

## 测试策略

测试目录：`src/test/java/org/jeecg/test/`，JUnit 5 + Spring Mock（不依赖真实数据库/网络），现有 11 个测试类，分三组：

- `security/`：安全回归用例，**以 GitHub issue 命名**（如 `Issue9677_SqlInjectionNewlineBypassTest`、`Issue9681_SsrfRedirectBypassTest`、`Issue9725_UploadImgByHttpSsrfTest`），修安全漏洞时必须补同类回归测试。
- `log/`：日志与 TraceId（`TraceIdFilterTest`、`EventLoggersTest`）。
- `sqlparse/`：SQL/IP 解析工具（`TestIpUtil`）。

用例要求：断言驱动（禁止 print 判断）、用例间无依赖、不影响外部环境。

## 安全注意事项

本模块是平台安全防线的集中地，改动以下区域要格外谨慎并补安全回归测试：

- **SQL 注入**：`common/util/SqlInjectionUtil`、`config/firewall/`（`DictSqlConditionCheckUtil`、字典表白名单 `IDictTableWhiteListHandler`）；SQL 必须走 MyBatis-Plus / `#{}` 参数绑定，**禁止字符串拼接 SQL**。
- **SSRF / 文件安全**：`common/util/security/`（上传文件类型校验、URL 校验）、`SvgSecurityFilter`。
- **鉴权**：新增匿名访问路径需改 `ShiroConfig` 的放行配置（含静态资源目录白名单 `ANONYMOUS_STATIC_RESOURCE_PATHS`）；内部接口走 `InnerApiGuardInterceptor`。
- **数据权限/多租户**：`config/mybatis/` 的拦截器与 `TenantContext` 是平台内置能力，新增查询不要绕过。
- 用户输入必须在边界校验；对外输出 HTML/文件注意转义；短信 key、OSS 密钥等敏感配置由启动模块注入，不要在本模块硬编码。

## 其他约定

- 执行删除类操作遵循迁移而非物理删除（`mv` 到隔离目录），严禁 `rm -rf`。
- 大范围搜索/读取时避开 `target/`、`.git/` 等目录。
