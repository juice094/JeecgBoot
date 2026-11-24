# 28 · Flowable 流程域 工程实施书（外部 Agent 线交接包）

> **【已归档 2026-11-23 · 被取代声明】** 本文档的 W1 施工内容已全部完成且不再适用：
> - W1 实际由**内部成员线**于 2026-11-19 施工完毕（commit `217449c3`，契约 AGT-2026-0003），非外部线承接；
> - 宿 2026-11-23 告知**外部承接方已不存在**，交接语义终止；
> - 本文保留作：①W1 验收对照史；②外部线若复活，W3/W4 交接包的**格式模板**（任务定义/仓库事实三层工作区/施工单出口条件/红线/验收回滚的结构可复用）。
> 现行计划真源 = 27 号立项书（charter，含波次状态与已拍决策）+ workspace 决策册。下方原文保留不改（审计痕）。

---

> 版本：v1（2026-11-19）· 交接对象：非 KimiCode 体系的外部 agent 线
> 上游：27 号立项草案（charter）、26 号治理设计（仅背景，本文规则已内联，无需读其他治理文档）
> 使用方式：交接 agent 的**会话第一条消息**使用 §五的初始提示词；本文是其唯一必读规格。

---

## 一、任务定义

在 JeecgBoot fork 上落地 Flowable 流程域服务（模块 `jeecg-module-flow`），完成 **W1 引擎落地** 的全部工作并交卷。W2-W4 为后续波次，本文只给边界不给细节。

一句话目标：**让 Flowable 8.0.0 引擎以正式模块形态在本 fork 启动、建表、跑通一条请假流程 REST 端到端，且不伤害在役系统。**

## 二、仓库事实（已查证，直接采信，勿全仓重扫）

| 项 | 事实 |
|---|---|
| 仓库根 | `C:\Users\22414\dev\JeecgBoot`（git fork，分支 `pilot/w1-trim`） |
| 后端 | `jeecg-boot/`：Java 17 + Spring Boot **4.1.0** + MyBatis-Plus 3.5.16，全程 jakarta 命名空间 |
| 模块清单 | 仅 `jeecg-boot-base-core` / `jeecg-module-system` / `jeecg-boot-module`（demo、airag、team、mail、**flow**）；上游 platform 线（含官方 Flowable 集成）**已被裁掉，仓库内不存在** |
| Flowable 版本 | **必须 8.0.0**（=Boot 4 兼容线起点；7.x 全线 Boot 3 不可用）。兼容性已实证 |
| 骨架现状 | `jeecg-module-flow` 模块已建：pom（flowable starter 已声明）、`FlowModuleConfig` 锚点类、`engine/delegate/listener` 包位、`resources/processes/` 目录；已注册进 `jeecg-boot-module/pom.xml`，**未注册进 system-start** |
| 尖刺残留 | `jeecg-module-demo` 内有参考实现：`flowable/controller/DemoFlowableController.java`、`flowable/service/DemoFlowableLeaveService.java`、`resources/processes/leave-process.bpmn20.xml`、pom 里 flowable 依赖。处置决策见 §四 W1-T3 |
| 在役环境 | 单体启动类 `jeecg-system-start`；**端口 8081 在役，严禁触碰**；验证一律用 `--server.port=8083` |
| 数据库 | MySQL `127.0.0.1:3306`，库 `jeecg-boot`，账号 root/root（仅开发库）；**已存在 39 张 `act_*` 表**（此前冒烟残留，引擎自管 schema，无需手工建表） |
| 构建 | `mvn`（JDK 17）；根 pom 默认 skipTests=true，跑测试须 `-DskipTests=false`；若见 Lombok"找不到符号"假错，前缀 `MAVEN_SKIP_RC=1` |
| 接口惯例 | 统一返回 `Result<T>`（`Result.OK(data)`/`Result.error(msg)`）；demo 模块匿名端点用 `@IgnoreAuth`；Swagger 注解 `@Tag`/`@Operation` |
| 前端 | `jeecg-team-web/`（Vue3+Vite，端口 3200 代理 /jeecg-boot→8081）；W1 不涉前端 |

## 三、工作区范围（写入白名单/只读/禁入三层）

**可写（write）**
- `jeecg-boot/jeecg-boot-module/jeecg-module-flow/**` —— 主施工面
- `jeecg-boot/jeecg-boot-module/jeecg-module-demo/**` —— 仅用于尖刺残留处置（§四 W1-T3）
- `jeecg-boot/jeecg-boot-module/pom.xml`、`jeecg-boot/jeecg-module-system/jeecg-system-start/pom.xml` —— 仅 W1-T4 接线一步允许改

**只读（read，禁止修改）**
- `jeecg-boot/jeecg-boot-module/jeecg-module-team/**`、`jeecg-module-mail/**` —— 在役业务模块（W3 才碰 team，本波只读参考其 Service/Controller 写法）
- `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/**` —— 配置只读；flowable 参数写本模块独立 yml 或 Java Config
- `projects/agent-collab-pilot/docs/`、`jeecg-team-web/docs/` —— 背景文档
- `jeecg-boot/db/`、flyway 目录 —— 只读（本波禁止新增 flyway 脚本，见 W1-T5）

**禁入（deny，任何情况下不读不写不传输其内容）**
- `C:\Users\22414\.kimi_openclaw\`、`.kimi-code\`、`.agents\` —— 其他 agent 体系的工作区与配置（含凭证）
- 任何 `.env`、`*credential*`、`*secret*`、`id_rsa*` 形态文件
- `**/node_modules/`、`**/target/`、`**/dist/` —— 构建产物
- `jeecg-team-web/src-tauri/target/`（体量大且无必要）

**凭证纪律**：§二给出的 root/root 仅限本机开发库使用；不得把任何凭证写进代码、日志、报告或提交内容。

## 四、W1 施工单（按序执行，每步有出口条件）

**W1-T1 依赖与编译基线**
确认 `jeecg-module-flow` pom 依赖可解析，模块 `mvn compile` 通过。出口：`BUILD SUCCESS`。

**W1-T2 引擎配置**
在模块内完成 Flowable 参数外置（独立 `application-flow.yml` 或 `FlowModuleConfig` Bean）：HistoryLevel=audit、异步执行器 dev 默认开、schema 策略保持默认（引擎自管，**不要**导出 DDL 进 flyway——这是已拍决策）。出口：配置有注释说明每项取舍。

**W1-T3 尖刺残留处置**
决策二选一并执行：①**移植**——demo 三件套移入 `jeecg-module-flow`（改包名 `org.jeecg.modules.flow.*`，controller 路径改 `/flow/demo/**`），然后还原 demo 模块（pom 移除 flowable 依赖、删除三个文件，用 git 状态核对干净）；②**重建**——在 flow 模块重写三件套，demo 面同样还原。推荐①（已有参考实现，重写是重复花钱）。出口：`git status` 中 demo 模块无 flowable 痕迹；flow 模块内流程可走。

**W1-T4 接线（本波唯一触碰在役面的动作）**
`jeecg-system-start/pom.xml` 加 `jeecg-module-flow` 依赖。**副作用预警：下次启动会在 jeecg-boot 库建/续用 39 张 act_\* 表（已存在则幂等）**，执行前在报告中显式声明此副作用。出口：全量 `mvn clean package -pl jeecg-module-system/jeecg-system-start -am` 通过。

**W1-T5 启动冒烟（8083，四验证）**
`--server.port=8083` 起单体，逐项留证据：①启动无 bean 冲突（日志无 APPLICATION FAILED）②`act_*` 表 39 张在位（information_schema 查询）③REST 端到端：start→approve→实例完结（curl 三发，路径读你落地的 controller）④在役面回归：`http://127.0.0.1:8083/jeecg-boot/team/task/board` 带 token 或匿名口径返回非 5xx。出口：四证据写入报告。完成后**关掉 8083 进程**。

**W1-T6 权限码预留（只生成清单，不改库）**
列出 W2 需要的权限码草案（如 `team:flow:process:list/start/approve`），写进报告**不执行** SQL——sys_permission 写入需宿确认后由功能线在 W2 统一做。

**本波明确不做**：team 审批链迁移（W3）、agent 门（W4）、前端、flyway 脚本、git push。

## 五、会话初始提示词（交接 agent 第一条消息，原样粘贴）

```text
你是 Flowable 流程域 W1 施工 agent。先读 C:\Users\22414\dev\JeecgBoot\projects\agent-collab-pilot\docs\28-flowable-implementation-handoff.md —— 它是你的完整施工规格（任务定义/仓库事实/工作区范围/施工单/红线/验收），读完再动手，规格内已查证的事实直接采信，不要全仓重扫。

最高优先级红线（违反即中止）：
1. 严禁 rm -rf 及任何物理删除；需要删除的文件用 git 还原或移动隔离。
2. 端口 8081 在役，严禁启动/停止/占用它；验证一律 8083。
3. 禁入清单（实施书 §三）：其他 agent 体系目录（.kimi_openclaw/.kimi-code/.agents）、任何凭证类文件——不读不写不外传。
4. 只改工作区，git 允许本地 commit（消息前缀 fix(flow):），严禁 push。
5. Flowable 版本固定 8.0.0，不得自行升降。
6. 本波不做实施书 §四"明确不做"清单内的任何事。

施工序：W1-T1 → T6 按序执行，每步过出口条件再进下一步；T4 接线前必须在报告里显式声明"将注册进 system-start，下次启动建/续 39 张 act_* 表"这一副作用。

收工报告必须包含：每步的验证证据（命令+exit/输出摘要，禁贴大段日志，错误给 文件:行+首个根因）/ git status 改动清单 / T6 权限码清单 / 驾驭面初评（引擎使用体感 5 行内）/ 未决问题。报告什么验证级就说什么级，不得夸大。
```

## 六、验收与回滚

- **验收人**：宿/主会话 agent 按 §四出口条件逐项复核，重点抽验 T5 四证据与 T3 的 demo 面还原干净度。
- **回滚路径**：全部改动未 push，回滚 = `git checkout/restore` 工作区 + demo 面核对；T4 的 system-start pom 改动单独可回滚（一行依赖）。回滚成本=低，已在立项时声明。
- **交接返回面**：W1 报告 + 驾驭面初评 + 未决问题清单 → 作为 W2 排期的输入。
