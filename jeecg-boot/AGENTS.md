# AGENTS.md

> 本文件为 AI 编码助手（Claude Code、Codex、Cursor、Kimi Code 等）提供仓库级指引。读者默认对本项目一无所知。除非用户明确要求其他语言，请优先使用简体中文回答。

## 项目概述

JeecgBoot 3.9.6 —— 免费开源（Apache-2.0）的 AI 低代码开发平台的后端工程（`jeecg-boot-parent`），采用 Maven 多模块结构，默认单体部署、可选 Spring Cloud 微服务。

- 技术底座：Java 17（兼容 21/24/25）+ Spring Boot 4.1.0 + MyBatis-Plus 3.5.16 + Apache Shiro 3.0.0 + JWT 4.5.0，全程使用 `jakarta` 命名空间（**不是** `javax`，新增代码禁止引入 `javax.*`）。
- 前端（现行默认）是仓库根下的 `jeecg-team-web/`（Vue 3 + TS + Vite，独立于 jeecgboot-vue3 的新工程），不在本目录内。
- 前端（旧，待迁移）`jeecgboot-vue3/`（Vue 3 + TS + Vite + Ant Design Vue）已标记为待迁移代码资产，不再承接新功能，仅作迁移参考；其细节见其自身 `AGENTS.md`。
- 默认账号 `admin / 123456`；本地启动后接口基址 `http://localhost:8080/jeecg-boot`，接口文档（Knife4j）`http://localhost:8080/jeecg-boot/doc.html`。

## 目录结构（本仓库实际内容）

```
jeecg-boot/
├── pom.xml                      # 根 pom：jeecg-boot-parent 3.9.6，Spring Boot 4.1.0 父工程
├── jeecg-boot-base-core/        # 核心框架：Shiro/JWT 鉴权、MyBatis-Plus 配置、通用工具、AOP、基类 Controller
├── jeecg-module-system/         # 系统管理（用户/角色/权限/字典/菜单/Quartz 等）
│   ├── jeecg-system-api/        # API 接口层：jeecg-system-local-api（单体直调）与 jeecg-system-cloud-api（Feign）二选一
│   ├── jeecg-system-biz/        # 业务逻辑、实体、Mapper、Service
│   └── jeecg-system-start/      # 启动入口 JeecgSystemApplication 与全部配置（application*.yml、flyway/、logback）
├── jeecg-boot-module/           # 业务功能模块（本检出仅含两个）：
│   ├── jeecg-module-demo        # 示例/演示代码
│   └── jeecg-boot-module-airag  # AI/RAG 集成（llm、知识库、向量检索、语音/视频/OCR 等）
├── jeecg-server-cloud/          # 微服务组件：Gateway(9999)、Nacos(8848)、cloud-start、jeecg-visual 监控等
├── db/                          # MySQL 初始化脚本 jeecgboot-mysql-5.7.sql、tables_nacos.sql、tables_xxl_job.sql、Dockerfile
├── docker-compose.yml           # 单体模式一键启动：MySQL(13306)、Redis、PG+pgvector、MongoDB、应用(8080)
├── CLAUDE.md                    # 后端详细说明（本文件与之互补，冲突时以实际代码为准）
└── online-form-schema.md        # Online 低代码表单配置 Schema 参考
```

注意：上游完整版还包含 `jeecg-boot-platform/`（BPM-flowable、bigscreen、desform、online 代码生成器等）及 easyoa/pay/wps 等模块，**本检出没有这些目录**，不要按上游文档假设它们存在；根 pom 的 `<modules>` 以实际列出的为准。

## 构建与运行

在 `jeecg-boot/` 下执行：

```bash
mvn clean package                                  # 全量构建（pom 默认 skipTests=true，测试被跳过）
mvn clean package -DskipTests=false                # 带测试构建
mvn clean package -pl jeecg-module-system/jeecg-system-start -am   # 只构建启动模块及其依赖
cd jeecg-module-system/jeecg-system-start && mvn spring-boot:run   # 本地启动（8080，context-path /jeecg-boot）
mvn clean package -P '!SpringCloud'                # 排除微服务模块（SpringCloud profile 在 pom 中默认激活）
mvn test -DskipTests=false -pl <module> -Dtest=<TestClassName>     # 跑单个测试类
```

### Maven Profile

- `dev`（默认激活）/ `test` / `prod` / `docker`：决定 `application.yml` 中 `@profile.name@` 过滤后的激活配置，并携带 Nacos 地址等属性。
- `SpringCloud`：在 pom 中 **默认激活**，追加 `jeecg-server-cloud` 模块；构建纯单体可显式排除。
- 私有仓库：`maven.aliyun.com` 与 `maven.jeecg.com`（积木报表、AutoPoi、jeecg-boot-starter-* 等私有构件由此拉取，构建失败先检查仓库可达性）。

### Docker

```bash
./start-docker-compose.sh        # Windows 用 start-docker-compose.bat（父目录 JeecgBoot/ 下）
```

docker-compose 服务：MySQL（宿主机 13306→容器 3306，root/root，库名 jeecg-boot）、Redis、PostgreSQL+pgvector（AI 向量库）、MongoDB、应用容器（8080）。`db/Dockerfile` 用 `db/jeecgboot-mysql-5.7.sql` 初始化数据库。

### 开发环境依赖

- 必需：MySQL 8.0+、Redis。
- 可选：MongoDB、RabbitMQ；AI 知识库需要 pgvector；AI 聊天需在 `application-dev.yml` 配置 `jeecg.ai-chat` 的 apiKey。
- `application-dev.yml` 默认连 `jdbc:mysql://127.0.0.1:3306/jeecg-boot`（root/root）、Redis `127.0.0.1:6379`，按本机实际环境修改。

## 技术栈速查

| 层 | 技术 |
|---|---|
| ORM | MyBatis-Plus 3.5.16（`BaseMapper<T>`、`ServiceImpl<M,T>`），动态多数据源 dynamic-datasource 4.3.1 + Druid 1.2.28 |
| 鉴权 | Shiro 3.0.0 + JWT 4.5.0，Redis 会话；RBAC + 按钮/数据/字段级权限 + 多租户 |
| 数据库迁移 | Flyway 7.15.0（脚本在 `jeecg-system-start/src/main/resources/flyway/sql/mysql/`，按年月分目录） |
| JSON / Excel | FastJSON 2.0.66；AutoPoi（`autopoi-spring-boot-3-starter`） |
| 接口文档 | Knife4j 4.5.0（OpenAPI v3，`@Schema`/`@Operation` 注解） |
| 定时任务 | Quartz（JDBC 存储，支持集群） |
| 文件存储 | MinIO / 阿里 OSS / 七牛，由 `jeecg.uploadType` 控制 |
| 报表 / 大屏 | JimuReport 2.5.6.1、JimuBI 2.5.6.1（积木报表/BI） |
| AI | langchain4j（`jeecg-boot-starter-ai`）；兼容 ChatGPT / DeepSeek / 千问 / Ollama；RAG 向量库 pgvector |
| 微服务 | Spring Cloud 2025.1.2 + Alibaba 2025.1.0.0（Nacos 3.2.2、Gateway、Sentinel、XXL-Job 3.4.2） |

## 代码组织与约定

### 后端

- 包结构：`org.jeecg.modules.<模块名>.{controller,entity,mapper,mapper.xml,service,service.impl,vo}`；系统级公共能力在 `org.jeecg.common.*`（api、aspect、constant、desensitization、exception、handler、system、util）。
- 实体：系统表以 `Sys` 前缀（如 `SysUser`、`SysRole`），标注 `@TableName`、`@TableId(type = IdType.ASSIGN_ID)`（雪花 ID），常用 `@Data`、`@EqualsAndHashCode(callSuper = false)`、`@Accessors(chain = true)`。
- Controller：`<Entity>Controller extends JeecgController<Entity, IService>`，基类自带标准 CRUD 与 Excel 导入导出。
- Service：接口 `I<Entity>Service extends IService<Entity>`，实现 `<Entity>ServiceImpl extends ServiceImpl<Mapper, Entity>`；Mapper 继承 `BaseMapper<Entity>`，XML 放 `mapper/xml/`。
- 统一返回包装：`Result<T>`（`org.jeecg.common.api.vo.Result`），用 `Result.OK(data)` / `Result.OK(msg, data)` / `Result.error(msg)`；数据在 `result` 字段，`success`/`code`/`message` 表示状态。
- 查询构造：`QueryGenerator.initQueryWrapper(entity, request.getParameterMap())` 依据 HTTP 参数自动组装 `QueryWrapper`（支持模糊、区间等），列表查询优先用它而非手写 Wrapper。
- 单体 ↔ 微服务切换：只改启动模块对 `jeecg-system-local-api` / `jeecg-system-cloud-api` 的依赖，不改业务代码。
- 数据库：默认 MySQL 8.0+，另支持 PostgreSQL、Oracle 11g+、SQL Server 2017+、MariaDB、达梦 DM8、人大金仓 KingBase ES，各库配置在 `application-{dbtype}.yml`（dm8/kingbase8/oracle/postgresql/sqlserver）。初始导入 `db/jeecgboot-mysql-5.7.sql`，增量靠 Flyway。
- 配置集中在 `jeecg-module-system/jeecg-system-start/src/main/resources/`：`application.yml`（Profile 选择、liteflow 配置导入、开发模式 `lazy-initialization: true`）、`application-dev.yml`（端口 8080）。`jeecg.*` 命名空间控制平台功能（上传方式、防火墙、AI、MinIO、Shiro 排除路径等）。

### 测试

- JUnit 5，测试默认跳过（根 pom `<skipTests>true</skipTests>`），显式用 `mvn test -DskipTests=false -pl <module> -Dtest=<类名>` 运行。
- 现有测试集中在 `jeecg-boot-base-core/src/test/java/org/jeecg/test/`（security、sqlparse 等，多为安全回归用例）；没有覆盖率门槛，核心逻辑改动建议补对应测试。

## 安全注意事项

- 所有用户输入必须在边界校验；SQL 一律走 MyBatis-Plus / `#{}` 参数绑定，**禁止字符串拼接 SQL**。
- 鉴权由 Shiro + JWT 统一处理，新增接口注意在 Shiro 配置中声明匿名/鉴权路径（`jeecg.shiro.*` 排除路径配置）。
- 接口有 AK/SK 鉴权的 OpenAPI 机制；平台敏感配置（数据库密码、AI apiKey）在 `application-dev.yml` 等文件中，提交代码时注意脱敏。
- XSS：输出 HTML/文件需注意转义；上传类接口注意文件类型/SSRF 校验（已有对应安全测试用例）。
- 数据权限/多租户是平台内置能力，新增查询不要绕过租户与数据权限拦截器。

## 其他约定

- 执行删除类操作遵循迁移而非物理删除（`mv` 到隔离目录），严禁 `rm -rf`。
- 大范围搜索/读取时避开 `target/`、`.git/` 等目录（可参考 `.claudeignore` / `.ignore`）。
- 仓库根目录的 `backend-dev.log` 是本地运行日志，不属于源码，不要提交或改动它。
- Online 低代码模块（上游 `jeecg-boot-module-online`）为**元数据驱动**：表单/报表配置存于数据库表（`onl_cgform_*`），代码仓库中读不到具体配置；需要时向用户索要 JSON 导出或截图。配置 Schema 详见 `online-form-schema.md`。
