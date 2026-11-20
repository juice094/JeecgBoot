# 22 — 接口文档待办事项

> 2026-11-17，Kimi Code 起草，juice094 待审定。
> 现状基线：Knife4j 已配双分组（团队协作 `/team/**` + 系统管理 `/sys/**`）；Apifox runbook 见 docs/15；团队规矩（docs/13 §二.4）：**改接口必须同步 Knife4j 注解，PR 描述带「文档影响」**。
> 本文档 = 接口文档域的待办清单，随开发节奏维护；完成项打勾并注日期。

## 一、待办清单

| # | 事项 | 责任 | 优先级 | 出口标准 | 状态 |
|---|---|---|---|---|---|
| 1 | **审计 `/team/task`、`/team/approval`、`/team/bug` 现有端点的 Knife4j 注解完备性**（@Tag/@Operation/@Parameter/@Schema 逐端点核对），补缺 | juice094 + bizflow-dev | P0 | 三个 Controller 全部端点有中文摘要、入参出参 @Schema 齐全 | ⬜（业务流转线推进中，状态机改动已带注解同步） |
| 2 | **业务流转新增端点入文档**：`GET /team/task/dueSoon`、`GET /team/dashboard/summary`（+ 可能的 `/team/task/board`）、审计埋点后端点行为说明 | bizflow-dev 写码带注解，juice094 复核 | P0 | 随 PR 同步；Apifox 重导可见 | ⬜ 待第二轮实现 |
| 3 | **物理删除端点契约变更文档化**（若宿拍板改逻辑删）：`/team/bug/delete*`、`/team/task/delete*` 响应语义变化写进 PR「文档影响」与本文档 | juice094 | P1 | 拍板后随 PR 完成 | ⬜ 挂起（等拍板） |
| 4 | **`/team/agent/**` 新接口文档**（docs/21 §三 10 个端点）：随 M4-α 开发同步 Knife4j + Apifox | agent 线（立项后定人） | P1（立项后升 P0） | 同 #1 标准 | ⬜ 未立项 |
| 5 | **前端 api 层与后端合同一致性核对**：`jeecg-team-web/src/api/team.ts` 端点 vs 后端实际（路径/方法/参数名），出差异表 | juice094 | P1 | 差异清零或开 issue 跟踪 | ⬜ |
| 6 | **仪表盘聚合接口契约**（docs/16 §四.3）：端点定型后与前端 dashboard 页面对字段（减少首页 N 次往返的目标可验收） | juice094 | P1 | 字段表双方确认 | ⬜ 依赖 #2 |
| 7 | **Apifox 项目维护**：按 docs/15 建项目（宿 5 分钟）+ 成员邀请 + 每月 1 日重导兜底；PR 模板中「Apifox 是否需重新导入」字段核对落地 | 宿（juice094） | P1 | 项目在线、成员齐、月检记录存在 | ⬜ |
| 8 | **VM 接口文档合同入口巡检**：`100.78.65.37:8080` nginx basic auth + jar 回环月检（docs/13 §五），确认云上 Knife4j 与 pilot/w1-trim 同步 | 宿 | P2 | 月检记录 | ⬜ |
| 9 | **airag 既有端点文档边界声明**：`/airag/flow/**` 在本 fork 404 的事实写进入口文档/团队 FAQ（防前端按商业版文档联调踩坑）；其余 airag 端点保持上游原样不补文档 | juice094 | P2 | FAQ/入口文档一段 | ⬜ |
| 10 | **内部接口清单归档**：`/sys/api/*`、`/airag/api/*` 等内部前缀（InnerApiBlockFilter 已拦外部）列一份清单进设计文档，防误当公开接口引用 | Kimi Code | P2 | 清单入 docs/17 或本文档附录 | ⬜ |

## 二、同步节奏（引用 docs/15 §四，不重复细则）

| 事件 | 动作 | 责任人 |
|---|---|---|
| 后端接口 PR 合并 | PR 标注「文档影响」+「Apifox 是否需重新导入」 | PR 作者 |
| 每月 1 日 | Apifox 全量重导兜底 | 宿 |
| 新增模块/分组 | Knife4j 分组配置同步（参考 commit 9394558e 双组做法） | 后端作者 |
| 契约变更（删/改端点） | 本文档 +1 行记录，breaking 变更开 issue 通知全组 | 变更作者 |

## 三、规矩重申（写进 review checklist）

1. 新端点三件套：`@Operation` 中文摘要、`@Schema` 入出参、Result 包装说明——缺一不合入
2. 前端联调以**本机 8081** 为准；VM 8080 仅合同对照（docs/13 §五）
3. 调试环境用 Apifox「本机调试」；登录 `POST /sys/login`（本机验证码已关）
4. 不把 basic auth 口令、模型 apiKey 写进任何文档/备注/示例（docs/15 §五）

---

*文档变更记录：2026-11-17 首版（吸收 bizflow-dev 首轮缺口清单的端点级结论）。*
