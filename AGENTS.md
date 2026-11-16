# AGENTS.md

> 本文件为 AI 编码助手（Claude Code、Codex、Cursor 等）提供仓库级指引。读者默认对本项目一无所知。除非用户明确要求其他语言，请优先使用简体中文回答。

## 项目概述

JeecgBoot 3.9.6 —— 免费开源（Apache-2.0）的 AI 低代码开发平台，采用前后端分离架构，默认单体部署、可选 Spring Cloud 微服务。

- **后端** `jeecg-boot/`：Java 17（兼容 21/24/25）+ Spring Boot 4.1.0 + MyBatis-Plus 3.5.16 + Apache Shiro 3.0.0 + JWT，全程使用 `jakarta` 命名空间（非 `javax`）。
- **前端** `jeecgboot-vue3/`：Vue 3 + TypeScript + Vite 8 + Ant Design Vue 4 + Pinia，源自 Vben Admin 深度定制。
- 默认账号 `admin / 123456`；后端默认 `http://localhost:8080/jeecg-boot`，接口文档（Knife4j）`http://localhost:8080/jeecg-boot/doc.html`，前端开发服务器端口 3100。

更详细的后端说明见 `jeecg-boot/CLAUDE.md`；前端目录另有独立的 `jeecgboot-vue3/AGENTS.md`（其内容仅适用于该子目录）。

## 仓库结构

```
JeecgBoot/
├── jeecg-boot/                  # 后端 Maven 多模块工程（根 pom: jeecg-boot-parent）
│   ├── jeecg-boot-base-core/    # 核心框架：Shiro/JWT 鉴权、MyBatis-Plus 配置、通用工具、AOP、基类 Controller
│   ├── jeecg-module-system/     # 系统管理（用户/角色/权限/字典/菜单）
│   │   ├── jeecg-system-api/    # API 接口层：local-api（单体直调）与 cloud-api（Feign）二选一
│   │   ├── jeecg-system-biz/    # 业务逻辑、实体、Mapper、Service
│   │   └── jeecg-system-start/  # 启动入口 JeecgSystemApplication 与全部配置
│   ├── jeecg-boot-module/       # 业务功能模块：demo、AI/RAG（airag）、easyoa、joa-flowable、pay、wps
│   ├── jeecg-boot-platform/     # 低代码平台模块：BPM-flowable、bigscreen、desform、drag、lowapp、online 代码生成器等
│   ├── jeecg-server-cloud/      # 微服务组件（-P SpringCloud 激活）：Gateway 9999、Nacos、Sentinel、XXL-Job 等
│   └── db/                      # MySQL 初始化脚本（jeecgboot-mysql-5.7.sql）
├── jeecgboot-vue3/              # 前端工程
├── docker-compose.yml           # 单体模式一键启动（MySQL 13306、Redis、PG+pgvector、MongoDB、应用 8080）
├── docker-compose-cloud.yml     # 微服务模式一键启动
├── start-docker-compose.{sh,bat} / start-docker-compose-cloud.{sh,bat}
└── check_jeecgenv.py            # 运行环境检查脚本（JDK/Maven/Node/pnpm）
```

## 构建与运行

### 后端（在 `jeecg-boot/` 下执行）

```bash
mvn clean package                                  # 全量构建（pom 默认 skipTests=true）
mvn clean package -DskipTests=false                # 带测试构建
mvn clean package -pl jeecg-module-system/jeecg-system-start -am   # 只构建启动模块及其依赖
cd jeecg-module-system/jeecg-system-start && mvn spring-boot:run   # 本地启动（8080）
mvn clean package -P SpringCloud                   # 含微服务模块构建
mvn test -DskipTests=false -pl <module> -Dtest=<TestClassName>     # 跑单个测试类
```

### 前端（在 `jeecgboot-vue3/` 下执行，Node 20.19+/22.12+，pnpm 10+）

```bash
pnpm install
pnpm dev            # 开发服务器，端口 3100，通过 VITE_PROXY 代理到后端
pnpm build          # 生产构建到 dist/（附带 postBuild/copyChat 脚本）
pnpm batch:prettier # 对 src/ 全量 Prettier 格式化，仅在需要大范围格式化时执行
```

环境变量见 `.env`（端口、标题、qiankun 开关）、`.env.development`（代理、mock、后端地址）、`.env.production`。关键变量：`VITE_GLOB_DOMAIN_URL`、`VITE_GLOB_API_URL`、`VITE_PROXY`。

### Docker

```bash
./start-docker-compose.sh        # Windows 用 start-docker-compose.bat
./start-docker-compose-cloud.sh  # 微服务模式
```

## 技术栈速查

| 层 | 技术 |
|---|---|
| ORM | MyBatis-Plus 3.5.16（`BaseMapper<T>`、`ServiceImpl<M,T>`），动态多数据源 Druid 1.2.28 |
| 鉴权 | Shiro 3.0.0 + JWT 4.5.0，Redis 会话；RBAC + 按钮/数据/字段级权限 + 多租户 |
| 数据库迁移 | Flyway（脚本在 `jeecg-system-start/src/main/resources/flyway/sql/mysql/`，按年月分目录） |
| JSON / Excel | FastJSON 2；AutoPoi（`autopoi-spring-boot-3-starter`） |
| 接口文档 | Knife4j 4.5.0（OpenAPI v3，`@Schema` 注解） |
| 定时任务 | Quartz（JDBC 存储，支持集群） |
| 文件存储 | MinIO / 阿里 OSS / 七牛，由 `jeecg.uploadType` 控制 |
| 工作流 / 报表 | Flowable（BPM）；JimuReport 2.5.6.1（报表）、JimuBI 2.5.6.1（大屏） |
| AI | langchain4j 1.17.2；兼容 ChatGPT / DeepSeek / 千问 / Ollama 等；RAG 向量库用 pgvector |
| 微服务 | Spring Cloud 2025.1.2 + Alibaba 2025.1.0.0（Nacos、Gateway、Sentinel、XXL-Job） |

## 代码组织与约定

### 后端

- 包结构：`org.jeecg.modules.<模块名>.{controller,entity,mapper,mapper.xml,service,service.impl,vo}`。
- 实体：系统表以 `Sys` 前缀（如 `SysUser`），标注 `@TableName`、`@TableId(type = IdType.ASSIGN_ID)`，常用 `@Data`、`@EqualsAndHashCode(callSuper = false)`、`@Accessors(chain = true)`。
- Controller：`<Entity>Controller extends JeecgController<Entity, IService>`，基类自带标准 CRUD 与 Excel 导入导出。
- Service：接口 `I<Entity>Service extends IService<Entity>`，实现 `<Entity>ServiceImpl extends ServiceImpl<Mapper, Entity>`；Mapper 继承 `BaseMapper<Entity>`，XML 放 `mapper/xml/`。
- 统一返回包装：`Result<T>`（`org.jeecg.common.api.vo.Result`），用 `Result.OK(data)` / `Result.error(msg)`；数据在 `result` 字段。
- 查询构造：`QueryGenerator.initQueryWrapper(entity, request.getParameterMap())` 依据 HTTP 参数自动组装 `QueryWrapper`（支持模糊、区间等）。
- 单体 ↔ 微服务切换：只改启动模块对 `jeecg-system-local-api` / `jeecg-system-cloud-api` 的依赖，不改业务代码。
- 数据库：默认 MySQL 8.0+，另支持 PostgreSQL、Oracle 11g+、SQL Server 2017+、MariaDB、达梦 DM8、人大金仓 KingBase ES，各库配置在 `application-{dbtype}.yml` Profile 中。初始导入 `db/jeecgboot-mysql-5.7.sql`，增量靠 Flyway。
- 配置集中在 `jeecg-module-system/jeecg-system-start/src/main/resources/`：`application.yml`（Profile 选择，由 Maven 的 dev/test/prod/docker 决定）、`application-dev.yml`（8080，开发模式启用 `spring.main.lazy-initialization=true`，Flyway 自动装配被显式排除、单独管理）。`jeecg.*` 命名空间控制平台功能（上传方式、防火墙、AI、MinIO、Shiro 排除路径等）。
- 开发环境依赖：MySQL、Redis 必需；MongoDB、RabbitMQ、pgvector 可选（AI 知识库需要 pgvector，AI 聊天需在 `application-dev.yml` 配置 `jeecg.ai-chat` 的 apiKey）。

### 前端

- 路径别名：`/@/` → `src/`，`/#/` → `types/`；代码中通篇用带斜杠的 `/@/`。
- 主要目录：`src/api/`（按域组织的请求函数）、`src/components/`（通用组件及 `jeecg/` 下平台专属组件）、`src/hooks/`、`src/layouts/`、`src/router/`（后端权限驱动动态路由）、`src/store/`（Pinia）、`src/views/`、`src/locales/`（zh_CN、en）。
- 页面惯例：每个功能模块通常含 `index.vue`（列表页）、`*.data.ts`（列与表单 schema）、`*.api.ts`、`*Modal.vue` / `*Drawer.vue`。
- 列表页用 `BasicTable` + `useTable`，弹窗用 `useModal` / `useDrawer`，标准 CRUD 页可用 `src/hooks/system/` 的 `useListPage`；表单 schema 用 `FormSchema[]`（组件如 `Input`、`JDictSelectTag`、`JSearchSelect`）。
- HTTP 客户端：`src/utils/http/axios/` 的 `defHttp`——拦截器注入 Token、MD5 请求签名、多租户请求头、按 `result` 字段解包。API 函数约定以 `enum Api { List = '/sys/user/list' }` 集中维护 URL。
- 权限模式为 `BACK`（后端驱动）：`src/router/guard/permissionGuard.ts` 拉取用户信息与动态菜单；按钮级权限用 `v-auth="'system:user:add'"` 指令或 `usePermission()`。分页参数为 `pageNo`/`pageSize`，响应取 `records`/`total`（见 `src/settings/componentSetting.ts`）。
- 代码风格：Prettier（行宽 150、单引号、ES5 trailing comma、分号、2 空格缩进）；ESLint 用 `vue/vue3-recommended` + `@typescript-eslint/recommended` + Prettier；新组件优先 `<script setup>`；样式用 Less；未使用变量加 `_` 前缀。
- 注意：`package.json` 中没有 `test` 脚本（虽有 Jest 依赖与 `tests/` 目录）；`build/` 目录是 Vite 插件与构建脚本，不是产物目录。

## 测试策略

- 后端：JUnit 5，测试默认跳过（`<skipTests>true</skipTests>`），用 `mvn test -DskipTests=false -pl <module> -Dtest=<类名>` 显式运行。没有强制的测试覆盖率门槛，核心逻辑改动建议补对应测试。
- 前端：Jest 29 + ts-jest + `@vue/test-utils` 已配置，但无 `test` 脚本；如添加前端测试需自行补齐脚本。
- `check_jeecgenv.py`：Windows 下的环境自检脚本（检查 JDK 17+、Maven、Node 20+、pnpm 9+ 并提示 MySQL/Redis/AI/pgvector 配置位置）。

## 安全注意事项

- 所有用户输入必须在边界校验；SQL 一律走 MyBatis-Plus / `#{}` 参数绑定，禁止字符串拼接 SQL。
- 鉴权由 Shiro + JWT 统一处理，新增接口注意在 Shiro 配置中声明匿名/鉴权路径（`jeecg.shiro.*` 排除路径配置）。
- 接口有 AK/SK 鉴权的 OpenAPI 机制；前端请求带 MD5 签名，改动 `defHttp` 时保持签名逻辑不被绕过。
- 平台敏感配置（数据库密码、AI apiKey）在 `application-dev.yml` 等文件中，提交代码时注意脱敏。
- XSS：前端渲染富文本使用 `xss` 库过滤；后端输出 HTML/文件需注意转义。
- 数据权限/多租户是平台内置能力，新增查询不要绕过租户与数据权限拦截器。

## 其他约定

- 执行删除类操作遵循迁移而非物理删除（`mv` 到隔离目录）。
- 大范围搜索/读取时避开 `node_modules/`、`dist/`、`target/`、`.git/`、`docker/data/` 等目录（各子目录 `.claudeignore` 可参照）。
- Online 低代码模块（`jeecg-boot-platform/.../jeecg-boot-module-online`）为**元数据驱动**：表单/报表配置存于数据库表（`onl_cgform_*`），代码仓库中读不到具体配置；需要时向用户索要 JSON 导出或截图。配置 Schema 详见 `jeecg-boot/online-form-schema.md` 与前端 `src/views/super/online/cgform/online-form-schema.md`。
