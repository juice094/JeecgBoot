---
title: 任务栏与布告 Issues 体系需求文档
docId: TASK-NOTICE-ISSUE-REQ-001
status: active
version: 1.0.1
owner: bizflow-dev（后端）/ juice094（business 线）
reviewers: 宿（juice094）/ lead
updated: 2026-11-17
---

# 任务栏与布告 Issues 体系需求文档

## 1. 背景与目标

团队协作的日常运转依赖三类信息载体，必须分线设计、分线验收：

| 主线 | 本质 | 典型场景 | 验收口径 |
|---|---|---|---|
| T 任务栏 | 协作执行 | 看板三列拖拽、指派、截止提醒 | 任务状态机可控、看板一次拉取流畅、临期可查询 |
| N 布告 | 信息广播 | 团队公告、通知 | 复用 jeecg 公告能力，不重复造轮子 |
| I Issues | 问题反馈 | Bug 提交、处理、验收、归档 | 状态机拦截非法流转，全程可审计 |

**目标**

- 任务/Issues 走显式状态机 + 审计留痕，禁止隐式流转。
- 布告复用 jeecg `sys_notice`，本体系不新建公告表。
- 看板、临期、聚合查询面向前端一次拉取设计，减少 N 次往返。

> 现状：三线接口均已落地（jeecg-module-team，`/team/task/**`、`/team/bug/**`；布告走 jeecg 既有公告接口）。本文随码维护。

## 2. 范围界定

**本期包含**

- 任务：CRUD、看板三列一次拉取、状态机流转、临期查询（截止提醒数据支撑）。
- 布告：复用 `sys_notice` 展示与已读口径；仪表盘聚合的 team 侧三计数。
- Issues：提交反馈、状态机流转（open/processing/resolved/closed）、列表追溯。

**本期不包含**

- 任务多级子任务/依赖关系、甘特图视图。
- 布告富文本编辑器与定时发布（jeecg 既有能力覆盖部分，按需另行立项）。
- 全文检索、附件上传（等 MinIO 拍板，docs/16）。
- 删除类操作的逻辑删除改造（挂起，待宿拍板，见 §8）。

## 3. 角色与权限

| 角色 | 权限 |
|---|---|
| 普通员工 | 创建/编辑任务；提交 Bug；流转本人相关单据 |
| 被指派人 | 在自己任务上流转状态；被临期查询命中 |
| 系统管理员 | 全量任务/Bug 管理；审计日志查询 |
| Agent（系统身份） | 封存线（docs/11），本期不实施 |

> 按钮级权限（`team:task:*`、`team:bug:*`）为 F-09 最小权限补丁内容，随宿拍板整体处理；当前所有登录用户可访问（试点骨架）。

## 4. 体系设计约束（需求侧可验收项）

### 4.1 分层与依赖

```text
接入方：OA 前端（jeecg-team-web src/modules/business）
   ↓
Controller 层 /team/task/**、/team/bug/**
   ↓
Service 状态机：TeamTaskStatus / TeamBugStatus 枚举 + changeStatus（校验 + update_by 回填 + 审计埋点）
   ↓
MyBatis-Plus（QueryGenerator 查询 + LambdaQueryWrapper 定制查询）
   ↓
MySQL：team_task / team_bug / team_audit_log
```

**必须满足的依赖规则**

1. 状态枚举为流转口径唯一来源（前端 `src/constants/task.ts` 与后端枚举对齐），禁止字面量散在代码中。
2. 状态变更必须经 service 层 `changeStatus`，controller 不得直接改状态；新建强制 `status=todo`、编辑强制回填库中原状态，二者均不接受客户端改状态（防绕过状态机）。
3. 每次状态流转同步写 `team_audit_log`（同事务，随业务回滚）。
4. 布告只读 `sys_notice`，本体系不直写 sys 表。

### 4.2 数据所有权

| 表 | 唯一写者 | 读方式 |
|---|---|---|
| `team_task` | team 模块 | 任务 API |
| `team_bug` | team 模块 | Bug API |
| `team_audit_log` | team 模块（TeamAuditLogger 只追加） | 审计 API |
| `sys_notice` | jeecg 系统模块（本体系只读） | jeecg 公告 API |

### 4.3 核心数据模型

**任务（team_task，V3.9.6_2）**

- `title`（必填）、`description`、`status`（todo/doing/done）、`tag`（需求/开发/缺陷）、`priority`（高/中/低）。
- `assignee_id` / `assignee_name`（被指派人及姓名冗余）、`due_date`（截止日期）、`sort_no`（看板列内排序）。
- jeecg 四字段 + `sys_org_code` / `sys_tenant_id`。

**Issues（team_bug，V3.9.6_3）**

- `description`（必填，≤2048）、`screenshot_path`、`status`（open/processing/resolved/closed）。
- `reporter_id` / `reporter_name`（提交人）、`handler_name`（处理人姓名）。
- jeecg 四字段 + `sys_org_code` / `sys_tenant_id`。

**审计（team_audit_log，V3.9.6_4，三体系共用）**

- `biz_type`（task/approval/bug）、`biz_id`、`biz_code`、`action`（submit/change_status/approve/reject）。
- `from_value` / `to_value`、`operator_id` / `operator_name`、`detail`。
- 只追加表：不提供更新/删除路径；索引 `(biz_type, biz_id)`、`(action)`、`(create_time)`。

**状态机**

- 任务：todo ↔ doing ↔ done 全互通（看板语义，done 非终态）；自流转非法。
- Issues：open → processing/closed；processing → resolved/open；resolved → closed/open；closed 终态。

### 4.4 DDL 约定

- 新表走 Flyway 脚本，编号在 V3.9.6_x 上顺延。
- jeecg 四字段必备；多租户沿用 `sys_tenant_id` 惯例。
- 常用过滤列建索引：`(status)`、`(assignee_id)` 等（见已落地脚本 V3.9.6_2/_3/_4）。

## 5. 功能需求

### 5.1 T 任务栏

| 编号 | 需求 | 优先级 | 验收标准 |
|---|---|---|---|
| T-1 | 任务 CRUD：分页列表（QueryGenerator 过滤）、新增（标题必填，状态/优先级默认值）、编辑、删除、批量删除、详情 | P0 | 列表支持 status/tag/priority/assigneeId 过滤；排序 sort_no asc, create_time desc |
| T-2 | 看板一次拉取：GET /board 返回 todo/doing/done 三组，组内 sort_no asc | P0 | 三列分组正确；脏状态不入列；响应结构稳定 |
| T-3 | 状态流转：PUT /status 走状态机，非法流转/非法状态码返回带上下文错误 | P0 | todo→doing/done、doing→todo/done、done→todo/doing 合法；自流转非法；update_by 回填 |
| T-4 | 临期查询：GET /dueSoon?days&scope=me，截止 ≤ today+days 且未完成 | P1 | days 限 0-90；scope=me 仅当前登录人被指派任务；due_date 升序 |
| T-5 | 仪表盘计数：未完成任务数（assignee=me 且 in todo,doing） | P1 | 随 /team/dashboard/summary 输出，口径与枚举一致 |

### 5.2 N 布告

| 编号 | 需求 | 优先级 | 验收标准 |
|---|---|---|---|
| N-1 | 公告展示复用 jeecg `sys_notice` 既有接口 | P0 | 前端经 jeecg 公告 API 拉取；本体系不新建公告表 |
| N-2 | 仪表盘公告聚合（一次拉齐公告/未读） | P2 | ✅ 已落地（D-72 宿裁授权例外）：summary 含未读数（sys_announcement_send read_flag=0）+最新已发布公告 5 条，口径见申请表流转体系接口文档 §6 |

### 5.3 I Issues

| 编号  | 需求                                           | 优先级 | 验收标准                                                                                                |
| --- | -------------------------------------------- | --- | --------------------------------------------------------------------------------------------------- |
| I-1 | 提交反馈：描述必填，状态强制 open，提交人取登录人                  | P0  | 空描述拒绝；reporter 字段服务端回填                                                                              |
| I-2 | 状态流转：PUT /status 走状态机                        | P0  | open→processing/closed；processing→resolved/open；resolved→closed/open；closed 终态拒绝；非法码拒绝；update_by 回填 |
| I-3 | 列表与追溯：分页列表（状态过滤）、详情                          | P0  | QueryGenerator 过滤；create_time desc                                                                  |
| I-4 | 仪表盘计数：未关闭 Bug 数（in open/processing/resolved） | P1  | 随 /team/dashboard/summary 输出                                                                        |
| I-5 | 删除与批量删除                                      | P1  | 试点期物理删除；逻辑删除改造挂起待宿拍板（§8）                                                                            |

## 6. 非功能需求

| 类别 | 要求 |
|---|---|
| 性能 | 看板/列表 P95 ≤ 500ms（试点数据量）；临期查询走 `(assignee_id)` / `(status)` 索引 |
| 可靠性 | 状态流转与审计同事务，审计不丢、随业务回滚 |
| 安全 | 登录态访问；状态机服务端强校验（不信任前端） |
| 兼容 | JeecgBoot 单体：Java 17 + Spring Boot 4.1 + MyBatis-Plus（jakarta 命名空间）；Knife4j 注解随码维护 |
| 审计 | 每次状态流转落 team_audit_log（biz_type=task/bug），/team/audit/list 可查 |

## 7. 迭代计划

| 期 | 交付内容 | 状态 |
|---|---|---|
| 一期 | T-1~T-3、I-1~I-3、N-1（CRUD + 看板 + 双状态机 + 公告复用） | ✅ 已落地（2026-11-17） |
| 二期 | T-4、T-5、I-4、I-5（临期 + 仪表盘 + 删除改造） | ✅ 已落地（2026-11-17，I-5 逻辑删挂起） |
| 三期 | 子任务/依赖、布告编辑器、附件（按需立项） | 未排期 |

## 8. 已知限制与挂起项

1. 任务/Bug 删除已改逻辑删（D-69a，V3.9.6_5，deleted 标志位 + @TableLogic），对外契约不变；team_approval 同加标志位备用。
2. Bug `handler_name` 为自由文本，无认领端点；处理人不可追溯。
3. 审批人/被指派人身份弱校验（姓名/ID 冗余），严格化需后续加列。
4. F-09 最小权限补丁（按钮级 @RequiresPermissions）整体处理中，本体系未局部加。

## 9. 验收清单

1. 看板：/board 返回三列分组，组内排序正确，脏状态丢弃。
2. 任务流转：todo→doing 成功；done→done 被拒并返回上下文文案；update_by 为当前登录人；新建任务 status 恒 todo（客户端传 done 无效），编辑接口不改 status。
3. 临期：scope=me 仅含本人未完成任务；days=0 命中今日到期。
4. Bug：open→resolved 被拒（须先 processing）；closed 后任何流转被拒。
5. 审计：任务/Bug 各流转一次后，`/team/audit/list?bizType=task&bizId=<id>` 可查 change_status 留痕；`changeStatus` 标注 `@Transactional(rollbackFor=Exception)`，状态与审计同事务、随业务回滚。
6. 公告：前端经 jeecg 公告接口展示，team 模块无 sys_notice 写路径。

## 10. 关联文档

- 接口文档：`任务栏与布告 Issues 体系接口文档.md`（同目录）
- 后端模块设计：`docs/16-backend-module-design.md`；协作规矩：`docs/13`；排期依据：`ROADMAP.md`（M3）
- 仪表盘聚合：见申请表流转体系文档 §6（/team/dashboard/summary 共用）
