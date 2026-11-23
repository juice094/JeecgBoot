# 20 — 原有 Agent 资产盘点与接入流转方案

> 2026-11-17，Kimi Code 起草（两个只读盘点子代理 + lead 抽查复核），juice094 待审定。
> 依据：docs/19（需求文档）；盘点范围 = 后端 `jeecg-boot-module-airag` + `jeecg-module-team`，前端 `jeecgboot-vue3` + `jeecg-team-web`。
> 验证级别：**静态阅读**（含对闭源 jar 的只读 ZipFile 常量池提取；sources jar 为 stub 已复核）；关键断言经 lead 重跑 2-3 条命令抽查。

## 一、资产总览

### 1.1 后端 airag 模块（org.jeecg.modules.airag，105 个 java 文件 ≈18.5k 行）

| 子包 | 资产 | 端点前缀 | 对本项目的价值 |
|---|---|---|---|
| app | AI 应用（chatSimple 普通 / chatFlow 流程两型）、SSE 会话、shareToken 匿名嵌入发布、AI 写作/海报 | `/airag/app`、`/airag/chat` | ★★★ 智能体的"躯体"——P1 智能体档案可直接落在应用层 |
| llm | 模型管理（langchain4j 1.17.2，provider：OPENAI 兼容/DEEPSEEK/OLLAMA/QWEN/QIANFAN/ZHIPU/ANTHROPIC/GOOGLE，类型 LLM/EMBED/IMAGE）、知识库（knowledge/memory 两型）+ 文档 + pgvector 向量检索、MCP 插件（sse/stdio） | `/airag/airagModel`、`/airag/knowledge`、`/airag/airagMcp` | ★★★ 模型与 RAG 底座直接复用 |
| flow（**闭源**） | AI 流程编排引擎 LiteFlow，26 种节点，表 `airag_flow`（design 存图 / chain 存 EL 链） | 本 fork **无** `/airag/flow/**` REST | ★ 引擎不可用作文档预期，见 §三风险 |
| prompts | 提示词 CRUD + 回收站 + `{{variable}}` 变量 + 在线实验 + 导入导出 | `/airag/prompts` | ★★★ S1-S5 流程线的提示词模板落点 |
| prompts/extData | 评估器 evaluator / 测试追踪 track | `/airag/extData` | ★ P5 观测可借鉴，较弱 |
| llm(api) | 流程节点内部 API：知识库写入、会话变量、取提示词、文件解析 | `/airag/api/*` | ★★ 编排层取数/写数的现成通道（内部接口，网关已拦外部访问） |
| ocr/video/voice/wordtpl | OCR 配置、视频/语音合成任务、Word 模板 AIGC | `/airag/ocr|video|voice|word` | ★ 与本需求弱相关，暂不接入 |
| demo | IAiRagEnhanceJava 流程增强扩展示例 | — | ★★ 闭源引擎上少数公开扩展点之一 |

**关键表**：`airag_app`（应用，含 modelId/knowledgeIds/prompt/memoryId/variables）、`airag_model`（模型配置，**credential 明文 JSON 存 apiKey**）、`airag_knowledge(+_doc)`、`airag_prompts`、`airag_mcp`、`airag_flow`（jar 内实体）、`airag_ext_data`。

### 1.2 后端 jeecg-module-team（自研，979 行，与 AI 零耦合）

- `/team/task`（team_task：todo/doing/done 看板）、`/team/approval`（team_approval + team_approval_step 顺序步骤状态机）、`/team/bug`（open/processing/resolved/closed）
- Flyway：V3.9.6_2 建 team_task，V3.9.6_3 建审批两表 + team_bug；V3.9.6_0 全为 ALTER/权限更新（无建表）
- **价值**：智能体团队"接任务"的落点就在这 4 张表；扩展方向 = 加 `assignee_type`（human/ai）与 ai 相关字段，不动状态机语义

### 1.3 前端 jeecgboot-vue3（AI 资产在 `src/views/super/airag/`，133 文件）

- 页面：AI 应用（含 11 个配置 Modal）、聊天（SSE + 工具卡 + portal 嵌入）、知识库、模型、提示词+回收站、MCP、OCR、Word 模板、视频/语音/海报/写作等
- 流程设计器：**不在 src 内**，在 `@jeecg/aiflow@3.9.6-beta` 包（logicflow 系），经 `registerPackages.ts` 按后端菜单 component 关键字懒加载
- 挂载：后端菜单驱动（sys_permission 表决定 AI 页面是否出现），chat 路由静态注册
- API 模式：colocated `*.api.ts`（12 个），`src/api` 下无独立 AI 目录

### 1.4 前端 jeecg-team-web（自研，端口 3200）

- 纯静态路由表 15 条（4 分组），侧栏从 router 派生（改路由表=改菜单，零额外配置）
- business 模块 9 个视图（申请/审批/Bug 走 `@/api/team`，其余 6 页静态）；**任务看板无实体页（无 TaskBoard.vue）**
- http 契约：`/jeecg-boot` baseURL + `X-Access-Token`（localStorage `team_token`）+ 成功码 200 调用点判定 + 15s 超时

## 二、差距判断：距"智能体团队"差 3 层

1. **无 Agent 一等公民抽象**：全模块无 agent 实体/注册/生命周期；现有"应用"= 提示词+模型+知识库，或单条流程
2. **工具链已成熟（最大加分项）**：function calling 体系完整——MCP 工具、**流程即工具**（AiragFlowPluginServiceImpl）、知识库检索工具、联网搜索、变量/记忆工具，多轮工具调用已处理。单智能体 ReAct 循环的原材料齐了
3. **编排引擎闭源且是"流程"不是"团队"**：无 agent 间消息传递/委托/并行聚合语义；`subflow` 是最接近"委托"的原语但不可改
4. **无共享任务/状态面**：AI 侧没有 team_task 的对应物；人类表与 AI 无任何接通代码
5. **记忆是应用级**：izOpenMemory/memoryId 挂在 app 上，无按 agent 隔离 + 团队共享记忆
6. **观测弱**：无 agent run 日志表、无 token/成本统计、flow 事件是 SSE 瞬时的
7. **结论**：差 **Agent 定义层 / 协作调度层 / 治理层** 三层；模型接入、工具调用、RAG、提示词四块底座**直接复用，不重写**

## 三、接入与流转方案

### 3.1 关键事实：流程编排引擎在本 fork 不可用

- `org.jeecg.modules.airag.flow.*` 由闭源混淆 jar `jeecg-aiflow-boot4-3.9.6.jar` 提供（sources jar 为 3 条目 stub，已复核）；jar 内无 Controller 类
- 前端 `/airag/flow/list|queryById` 等流程管理接口在本 fork **404**；ShiroConfig 登记的 `/airag/flow/run|debug|code/generate` 是防御性登记，运行时同样无实现
- 本 fork 实际可用的流程入口只有：`POST /sys/api/runAiragFlow`、`/sys/api/runAiragFlowStream`（SystemApiController → IAiragFlowService，需鉴权/内部调用）、chatFlow 型应用经 `/airag/chat/send` 间接执行
- 闭源引擎的可扩展缝：① `IAiRagEnhanceJava`（JAVA 增强节点）② `/airag/api/*` 内部 API ③ MCP/插件机制

### 3.2 接入策略：三档分类

| 档位 | 资产 | 策略 |
|---|---|---|
| **直接复用** | 模型管理、知识库/RAG、提示词管理（`{{variable}}`）、chatSimple 应用 + SSE 会话、shareToken 嵌入、MCP | 零改动；P1 智能体档案直接映射 airag_app + airag_prompts |
| **包装使用** | chatFlow 流程应用（经 `/sys/api/runAiragFlow` 触发）；流程即工具（function calling）；IAiRagEnhanceJava | 只通过公开入口调用，不进引擎内部 |
| **新建（team 侧）** | Agent 档案扩展、业务流程线编排、人机确认节点、运行留痕 | 在 `jeecg-module-team` 新建 `agent` 子包（或独立 `jeecg-module-agent`），见 docs/21 |

### 3.3 流转方案（数据流）

```
业务事件（审批提交/任务创建/Bug 提交/问答请求）
   → team-agent 编排服务（新建，事件订阅 + Quartz 定时兜底）
   → 取数：team_* / sys_notice 等（服务端查询，走数据权限拦截器）
   → 组装提示词：引用 airag_prompts 模板 + {{variables}} 注入业务数据
   → 执行（两选一）：
       A. 调 chatSimple 应用会话（/airag/chat/send 或内部服务直调，复用记忆/变量能力）
       B. 经 IAiragFlowApi.runFlow 跑预置流程（仅当流程线确需分支/循环时）
   → 人工确认节点（team_agent_run 挂起 → 确认/驳回 → 继续）
   → 写回业务表（team_approval 预审摘要字段 / team_bug 分类建议 / sys_message 提醒）
   → team_agent_run 留痕（输入/输出/token/耗时/确认人）→ M3 审计日志
```

**重要判断：MVP（M4-α 的 S4 公告问答、M4-β 的 S1 审批预审）只需"取数 → 模板提示词 → chatSimple 应用 → 人工确认 → 写回"直链，不依赖闭源流程引擎**。流程线先以"编排模板表 + 代码编排"实现（每条业务线一个 Spring Service），等 jeecg 商业版接口或自研编排器成熟后再迁移到画布编排——避免被闭源 jar 卡死。

### 3.4 智能体身份与权限流转

- 推荐方案 A（起步）：智能体 = 特殊 `sys_user`（用户名如 `agent-审批预审`，角色走 RBAC，数据权限随角色）——权限体系零新建，Mattermost bot 映射与 sys_message 触达都顺
- 备选方案 B（远期）：独立主体 `team_agent` + 独立权限映射表——隔离更彻底但全链路（登录/审计/Mattermost）都要新做
- 智能体对业务表的写操作**一律走服务端 API + 数据权限拦截器，禁止直库写**；运行身份 = 触发人身份，跨人取数必须过授权节点

### 3.5 安全与已知债（立项时一起排）

| 项 | 现状 | 建议 |
|---|---|---|
| 模型密钥 | `airag_model.credential` 明文 JSON | M4-α 前至少做脱敏审计；中期接 jeecg 加密或 KMS |
| 匿名嵌入 | shareToken + 限流 + 租户隔离已加固（GHSA 系列修复） | 复用，不自建 |
| 提示词注入 | 业务数据注入 prompt 无边界校验 | 编排层加输入校验 + 输出过滤 |
| 内部 API | `/airag/api/*`、`/sys/api/*` 网关已拦外部访问 | 编排层调用走内网/Feign，勿暴露公网 |

## 四、给需求文档的回馈（修订建议）

1. docs/19 §五.2"引擎：优先复用 airag AI 流程编排"**降级**为"包装使用 chatFlow 入口；流程线 MVP 用模板表 + 代码编排"，原因即 §3.1 闭源事实
2. docs/19 §七开放问题"智能体身份模型"：本方案推荐方案 A（特殊 sys_user），开发文档 docs/21 给出对比
3. 新增非功能需求：airag 密钥明文债的处置排期

---

*文档变更记录：2026-11-17 首版。*
