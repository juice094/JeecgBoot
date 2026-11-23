# 25 — 快速演示包设计（数据种子 · 演示路径 · 一键脚本）

> 2026-11-17，showcase-dev 起草。**本轮只设计不施工**：后端注解施工等 bizflow-dev 第四轮收口后第二轮排期；种子脚本需宿审定后才允许执行。
> 解决痛点③「有及时演示需求时无法快速出效果」。配套：docs/24（前端资产地图）、docs/22 #1（接口文档待办）。

## 一、目标与现状差距

| 项 | 现状 | 差距 |
|---|---|---|
| 可演示数据 | dev 库只有空表结构（V3.9.6_2~6 全是 DDL/权限） | 看板空、审批空、仪表盘全 0，演示「讲不出故事」 |
| 演示路线 | 无文档，靠即兴 | 需要一条 5 分钟、每步有口播要点与兜底的动作脚本 |
| 环境自检 | `scripts/smoke-team-stack.sh` 只查端口通断 | 不能证明「数据就绪、守卫生效」 |
| 成果展示 | team-web 五页已接真接口 | 痛点①的展示面已有，缺数据与编排 |

三件套：**演示数据种子**（§二）、**5 分钟演示路径**（§三）、**一键演示脚本**（§四）。

## 二、演示数据种子（V3.9.6_7 草案）

草案文件：`projects/agent-collab-pilot/sql/V3.9.6_7__team_demo_seed.draft.sql`（**未执行**；转正后移入 `jeecg-system-start/src/main/resources/flyway/sql/mysql/` 手动执行并登记 flyway_schema_history，规矩同 V3.9.6_6 头注）。

### 2.1 数据构成

| 域 | 数量 | 覆盖目标 |
|---|---|---|
| team_task | 9（todo/doing/done 各 3） | 三列分布；标签/优先级/指派人混合；临期 +1d/+2d 各 1、过期 -1d 1、远期 +5d/+7d——`dueSoon` 口径随执行日恒成立 |
| team_approval | 5 + 9 步骤 | 全状态链：pending@step1、pending@step2（步骤1已批）、approved（两步全批）、rejected（带拒绝意见）、pending@试点链（贺致远→黄茜茜） |
| team_bug | 4 | open/processing/resolved/closed 四态全链 |
| team_audit_log | 14 | submit/change_status/approve/reject 四类动作全有存量，`bizId` 追溯开箱可见 |
| sys_announcement + send | 3 + 3 | admin 视角 2 未读 1 已读；dashboard 未读计数=2 |

### 2.2 关键设计决策

1. **admin 可批链**：demo-appr-001/002 步骤1 审批人按 `@n_admin`（admin 的 realname）解析——`todo` 查询与 approve 守卫都按 realname 比对，保证 **admin 登录即可完整演示审批通过与越权守卫**；demo-appr-005 保留试点硬编码链（贺致远→黄茜茜），展示「新提交申请」的真实形态。
2. **不写死用户 id**：按 username/realname 动态解析；试点库缺 贺致远/黄茜茜 时相关列落 NULL，主链路（admin）不受影响。
3. **幂等**：全部 demo- 固定主键，先删后插，重跑安全；不碰非 demo 数据。
4. **相对日期**：一律 `CURDATE() ± INTERVAL`，种子任何一天执行，临期/过期演示口径都成立。
5. **审计轨迹与业务单严格一致**（current_step/status ↔ 步骤表 action），避免「审计追溯」演示时出现对不上的尴尬。

### 2.3 执行与自检

手动执行于 dev 库（试点 Flyway 自动装配已排除）。脚本尾部自带 5 条自检 SQL，期望：`task 3/3/3`、`approval pending×3/approved×1/rejected×1`、`bug 四态各 1`、`audit 按 biz_type/action 分布 14 行`、`send read_flag: 0×2 + 1×1`。

### 2.4 风险

- `listByUser` 实际过滤口径（近一月窗口、send 联查方式）以联调为准，公告种子若未显示先查该口径（F-01/F-08，见 `src/api/announcement.ts` 头注）。
- `sys_announcement` 为 utf8mb3，种子内容已避开 emoji/生僻字。
- 执行权限：写库操作属宿授权项，本轮只交草案。

## 三、5 分钟演示路径

预设：种子已执行；`bash scripts/start-team-backend.sh` + `start-team-web.sh` 已起；浏览器开 3200。账号 admin / 123456（试点验证码已关）。

| 时刻 | 步骤 | 动作与口播要点 | 背后端点 | 兜底 |
|---|---|---|---|---|
| 0:00 | 环境开场 | 跑一键脚本（§四）全绿：「端口通、数据就绪、守卫生效，三条命令内的自信」 | smoke + /sys/login + 7 项体检 | 任一 FAIL 按脚本提示修复；修不了就跳 3 分钟录屏（第二轮产出） |
| 0:30 | 登录 → 仪表盘 | admin 登录；三计数（待审 1+/未完成任务/未关闭 Bug）+ 公告未读 2 条：「不是 mock，是聚合接口真查」 | `/team/dashboard/summary`、`/sys/annountCement/listByUser` | 计数为 0 → 种子未执行，回 §二 |
| 1:00 | 任务看板 | 三列各 3 卡；**拖拽** demo-task-001 todo→doing（合法流转落库）；口播「状态机服务端强校验，前端只是通道」 | `/team/task/board` → PUT `/team/task/status` | 拖拽失手可用按钮流转，效果等同 |
| 1:45 | 状态机守卫 | Bug 反馈页：对 demo-bug-004（closed）尝试重开 → 服务端拒绝「closed 终态」；口播「非法流转不靠前端禁按钮，靠服务端状态机」 | PUT `/team/bug/status`（期望失败） | 页面无 closed 重开入口时，切 doc.html 在线调试复演（更有说服力） |
| 2:30 | 审批中心 | tab 四态有单；打开 demo-appr-002 看**步骤链时间线**（一步已批带意见、一步待批）；admin 通过 demo-appr-001 当前步骤；再对 demo-appr-005（当前审批人贺致远）点通过 → **越权拒绝**「越权审批：当前步骤审批人为 贺致远，登录人为 管理员」 | `/team/approval/todo`、`/steps`、`/approve` | 越权演示是高潮段，务必保留；doc.html 复演口径：POST /team/approval/approve?id=demo-appr-005 |
| 3:30 | 审计追溯 | doc.html「团队协作」分组 → `/team/audit/list`：`bizType=approval&bizId=demo-appr-003` 全链 submit→approve→approve 三条；换 demo-task-007 看两次 change_status；口播「每个动作事务内埋点，只追加不可篡改」 | `/team/audit/list` | 前端暂无审计页（属 docs/24 第二轮 A 档候选），doc.html 调试页演示反而更显 API 完备 |
| 4:15 | 收口 | doc.html 双分组文档页 + Knife4j 在线调试；预告 docs/24 A 档监控页（日志/定时任务/在线用户）落地后「后台完整性」展示面 | doc.html | — |

全程 5 分钟，每步都落在**已验收的后端能力**（状态机/守卫/逻辑删/审计/聚合）上，不讲未做功能。

## 四、一键演示脚本设计（`scripts/demo-showcase.sh`，草案不施工）

```
bash scripts/demo-showcase.sh [阶段]   # 默认 --all；阶段: --check | --guard | --open
```

| 阶段 | 内容 | 判定 |
|---|---|---|
| 0 自检 | 复用 smoke 端口检查（source 不重写） | doc.html 200；/team/** 401/403 视为通 |
| 1 登录 | `POST /sys/login`（admin/123456，试点无验证码）解析 `result.token` | 无 jq 依赖，用 python 一行解析；取不到 token 直接 FAIL 退出 |
| 2 种子体检 | 带 `X-Access-Token` 断言 7 项 | board 三列各 ≥3；dueSoon 非空 ≥2；approval/todo total ≥1；bug/list total ≥4；audit/list total ≥10；dashboard/summary 三计数 >0；listByUser 公告 ≥1 |
| 3 守卫自检（--guard） | 只读式触发：对 demo-bug-004 发 `status=open`、对 demo-appr-005 用 admin 发 approve | 两者都必须 `success=false` 且 message 含守卫文案；**断言失败=守卫回归，演示禁止开始** |
| 4 打开页面（--open） | `cmd //c start` 依次打开 3200 演示路线 4 个 URL | Windows Git Bash 兼容；非 Windows 降级为打印 URL 清单 |

退出码：0 全绿 / 1 环境或守卫 FAIL / 2 种子缺失。与 smoke 的关系：smoke 保持最小通断进版本库日常；demo-showcase 是**演示日前专用编排**，独立文件不合并。施工量：脚本本体约 150 行 bash，第二轮随种子转正一并交付。

## 五、风险与依赖

1. **bizflow-dev 收口前不动 team 代码**——附录 A 的注解补缺全部排在第二轮。
2. 种子写库需宿显式授权（草案 → 审定 → 手动执行）。
3. 审批 todo 已知限制（步骤表按姓名匹配，重名互见）——演示用 admin 专属链规避，不在演示中主动提及。
4. 演示依赖 8081/3200 双端已起；一键脚本承担「未起服务」的明确报错而非自动拉起（拉起逻辑已在 start-*.sh）。

---

## 附录 A：Knife4j 文档缺口审计（/team/** 24 端点）

> 审计时间 2026-11-17 傍晚（bizflow-dev 第四轮收口前快照，只列清单不改代码）。
> 范围：jeecg-module-team 五个 Controller 全部端点。**不含 `/team/mail/**`**（mail-dev 边界，其文档归 mail 线）。
> 图例：✅达标 / ⚠️有缺口（标注类型）。类型三档：**注解**=补注解即可；**VO**=需补 VO/请求体注解；**示例**=需补响应示例。

### A.1 汇总发现

- **F1 摘要**：24/24 `@Operation` 中文摘要齐全，状态机/看板/todo/删除等 7 端点带 description 补充——**摘要维度已达标**（第四轮成果），缺口集中在以下四项。
- **F2 入参 `@Parameter`**：仅 3/24 端点覆盖（task dueSoon 的 days/scope、task queryById 的 id）。其余 query 参数（id/ids/status/comment/pageNo/pageSize）在 Knife4j 页面上无名无说明——**补缺仅需注解**，约 20 处。
- **F3 请求体 VO**：`TeamApprovalSubmitBody`（内部类，approval submit 的 body）类与两字段均无 `@Schema`，Knife4j 上提交申请「请求体无字段说明」——**唯一需补 VO 注解项**。
- **F4 响应示例**：实体/VO 字段级 `example` 共 13 处（状态码/计数类），`TeamDashboardSummary` 是最佳样板；但无 `@ExampleObject` 级响应示例，Result 包装（success/message/code/result/timestamp）无全局说明——**需补示例（可选增强）**，建议在 Knife4j 分组文档说明 Result 包装与全局错误语义，成本低于逐端点 `@ApiResponse`。
- **F5 错误场景**：业务错误文案全部在 service 层（`JeecgBootException` 中文消息），文档零沉淀。审批四端点是守卫演示核心（非 pending/重复审批/**越权**/自审四类错误），建议 description 各补一句错误场景——**补缺仅需注解**。

### A.2 端点级清单

| # | 端点 | 摘要 | 入参 | 响应示例 | 错误场景 | 缺口类型 |
|---|---|---|---|---|---|---|
| 1 | GET /team/task/list | ✅ | ⚠️ pageNo/pageSize 无说明 | 实体 Schema 全 | — | 注解 |
| 2 | POST /team/task/add | ✅ | body Schema 全 | 字段无 example | ⚠️ 标题空校验未写 | 注解 |
| 3 | PUT /team/task/edit | ✅ | body Schema 全 | 同上 | ⚠️ 归属校验未写 | 注解 |
| 4 | PUT /team/task/status | ✅（状态机入摘要） | ⚠️ id/status 无说明 | — | ⚠️ 非法流转文案未写 | 注解 |
| 5 | GET /team/task/dueSoon | ✅ | ✅ 唯二覆盖端点 | days/scope 有说明 | ⚠️ days 0-90 校验未写 | 注解 |
| 6 | GET /team/task/board | ✅（含响应结构 description） | 无入参 | ⚠️ 无 example | — | 示例(可选) |
| 7 | DELETE /team/task/delete | ✅（逻辑删+归属入 description） | ⚠️ id 无说明 | — | — | 注解 |
| 8 | DELETE /team/task/deleteBatch | ✅ | ⚠️ ids 逗号格式未说明 | — | — | 注解 |
| 9 | GET /team/task/queryById | ✅ | ✅ id 有说明 | — | — | — |
| 10 | GET /team/approval/list | ✅（tab 过滤入摘要） | ⚠️ pageNo/pageSize | 实体 Schema 全 | — | 注解 |
| 11 | POST /team/approval/submit | ✅ | ⚠️ **SubmitBody 无 Schema** | — | ⚠️ 4 类校验未写 | **VO** + 注解 |
| 12 | POST /team/approval/approve | ✅ | ⚠️ id/comment 无说明 | — | ⚠️ 越权/重复/自审未写（**演示核心**） | 注解 |
| 13 | POST /team/approval/reject | ✅ | ⚠️ 同上 | — | ⚠️ 同 approve | 注解 |
| 14 | GET /team/approval/steps | ✅ | ⚠️ id 无说明 | Step Schema 全 | — | 注解 |
| 15 | GET /team/approval/todo | ✅（含已知限制说明，最佳实践） | ⚠️ pageNo/pageSize | — | — | 注解 |
| 16 | GET /team/approval/queryById | ✅ | ⚠️ id 无说明 | — | — | 注解 |
| 17 | GET /team/bug/list | ✅ | ⚠️ pageNo/pageSize | 实体 Schema 全 | — | 注解 |
| 18 | POST /team/bug/add | ✅ | body Schema 全 | — | ⚠️ 描述空校验未写 | 注解 |
| 19 | PUT /team/bug/status | ✅（状态机入摘要） | ⚠️ id/status 无说明 | — | ⚠️ closed 终态等未写 | 注解 |
| 20 | DELETE /team/bug/delete | ✅（逻辑删入 description） | ⚠️ id 无说明 | — | — | 注解 |
| 21 | DELETE /team/bug/deleteBatch | ✅ | ⚠️ ids 格式未说明 | — | — | 注解 |
| 22 | GET /team/bug/queryById | ✅ | ⚠️ id 无说明 | — | — | 注解 |
| 23 | GET /team/dashboard/summary | ✅（三计数入摘要） | 无入参 | ✅ 11 字段全带 example（**样板**） | 未登录语义全局未声明 | 示例(全局) |
| 24 | GET /team/audit/list | ✅（含过滤示例 description） | 实体字段即过滤条件，可用 | 实体 Schema 全 | — | — |

### A.3 第二轮施工量估算

| 工作项 | 类型 | 量级 |
|---|---|---|
| @Parameter 补齐（约 20 处） | 注解 | 半天内 |
| TeamApprovalSubmitBody 补 Schema | VO 注解 | 半小时 |
| 关键端点错误场景 description（#4 #11 #12 #13 #19 等） | 注解 | 半天内 |
| 分组文档 Result 包装 + 401/403/业务错误全局说明（doc 22 #1 出口标准） | 示例/文档 | 一小时 |

合计约 1 人日，无 breaking 变更，随 bizflow-dev 收口后一个 PR 落地；落地后 docs/22 #1 可打勾，演示路径 §三「审计追溯/在线调试」段的文档可信度同步提升。

---
*文档变更记录：2026-11-17 首版（三件套设计 + 24 端点 Knife4j 审计附录）。*
