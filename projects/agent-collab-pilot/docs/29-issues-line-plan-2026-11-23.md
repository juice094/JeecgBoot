# 29 · /issues 功能线方案——对齐 GitHub /issues × DSH 班组语义（2026-11-23）

> 状态：已立项（宿指令「对齐 github 的 /issues 功能面推进开发，融合 dsh-deck-brief 设想」）
> 上游：dsh-deck-brief.md（C:/Users/22414/dev/projects/inspector-mvp/docs/，2026-11-09 事实简报）§3 智能体班组
> 现状基线：IssueList 354 行（仅客户端 kind/state 二筛选）；TeamTask 已有 tag 单字段/assignee/优先级/到期；/list 走 QueryGenerator 服务端过滤（已实证 TeamTaskController.java:77）

## 定位一句话

把 /issues 做成「人类与 agent 共用的工作项系统」：功能面对齐 GitHub /issues，协作语义与 DSH 班组任务板（CAS 认领/阻塞依赖/改派）同源——**同一套动词，人 agent 通用**（身份底座唯一原则的产品化）。

## 波次

### P0 对齐基底（前端为主，后端零改动）——已开工 11-23
- IssueList 重写：服务端筛选（status/assigneeId/priority/tag/createBy 走 QueryGenerator 等值+title 模糊）、搜索框（#编号/标题）、排序切换（创建/到期/优先级）、分页
- 列表项 GitHub 式：状态点、#task_no、标签 chips（吃现有 tag 字段）、指派人、到期日、open/closed 语义（六态映射 open=todo|doing，closed=done）
- 批量操作：批量指派/关闭（F-09 归属校验沿用）
- 详情页补齐：评论（已有）/时间线（audit log 已有）呈现对齐 GitHub conversation 流

### P1 协作语义（后端+迁移，排队中——W3 后端完工后开工，避免 team 模块双开）→ **已开工 11-23 夜**
- **聚合端点（宿裁「P1 补聚合」）**：`GET /team/issue/list` 契约定稿——入参 kind(all|task|bug|approval)/keyword/assigneeId/state/pageNo/pageSize；出参统一行 {kind, code(#T-12|#B-3|OA单号), bizId, title, state, assigneeName, labels[], blocked, createTime, dueDate?}；service 三源合并内存分页（ponytail 注释上限）。agent-8（team 后端）+ agent-10（前端）施工中
- 标签体系：team_label + team_task_label，CRUD perm team:task:edit，/team/task/list 行内嵌 labels（原 tag 字段保留兼容，不自动迁移）
- 依赖/阻塞：team_task_dependency 双列唯一；blocked=标记位（**六态零改动，未决①按推荐落定**）；doing 前置校验依赖全完成
- 认领 claim（自助，审计 CLAIM）/改派 reassign（TeamOwnershipGuard，审计 REASSIGN）
- flyway V3.9.6_16（三表+blocked 列，checksum 真实）
- 前端顺带：ApprovalCenter 单横线 tag class 存量修复 + api/flow.ts 请假改指统一端点（D-94）

### P2 DSH 融合（跨运行时，HTTP 契约零耦合）
- issue 详情「派工」动作 → dsh 派工面 POST /run（prompt=issue 上下文, cwd=工作区）→ run 状态/事件流（GET /runs/:id?tail）嵌回 issue 时间线
- 承接数字员工只读信息面（排班/休假观察=banUntil chips，叙事映射按 brief §1 治理表）
- 红线：dsh 是另一运行时，只走 HTTP；brief 禁区照守（不夸大员工能力、实验性标注）

## 验收口径

- P0：typecheck+build 绿；筛选/搜索/排序/分页对 /list 真实请求留证（编译级+请求级）
- P1：模块测试绿+8083 冒烟；状态机负路径（blocked 流转非法）有测试
- P2：端到端「issue→派工→事件流回流」一条真实 run 留证

## 未决（需宿拍板）

1. P1 blocked 形态：扩六态枚举 vs 独立标记位（施工提案带推荐：标记位，状态机零冲击）
2. P2 派工凭证面：dsh /run 的 KIMI_CODING_API_KEY 透传与 JeecgBoot 侧无关，但 issue→run 的 prompt 模板与回调轮询频率需定稿

## 须知：/issues 与 /tools/taskboard 语义关系（2026-11-23 宿提示登记）

**现状重合（实证）**：
- `/tools/taskboard`（TaskBoard.vue）= TeamTask 执行面：board 三列 + **默认列表模式（代码注释自述「对标 GitHub Issues」）** + 增删改/拖拽流转全操作
- `/issues`（IssueList.vue）= 跨 kind 聚合（任务+缺陷+审批三源客户端合并）+ 统一四态（open/active/done/closed）+ 只读分流
- 两个「类 GitHub 任务列表」并存 = 漂移面（筛选口径/状态语义/操作入口双份维护）

**定位切分提案（P1 落地，P0 不动 TaskBoard）**：
- `/issues` = **跨 kind 分流/triage 唯一列表面**（GitHub /issues 真身）；`/tools/taskboard` = 执行看板 + 快捷操作（对齐 GitHub **Projects** 而非 Issues）；TaskBoard 列表模式收敛为看板附属或共享组件
- **统一编号**：显示 `#<KIND>-<no>`（T-12/B-3/OA-006），三域单号并存现状保留、展示层归一

**缺失清单（本线要补的）**：
1. bug 无 GitHub 式详情页（BugReport 是表单页）→ issue 详情扩 kind，复用 comment/timeline 基建（audit 已有 BUG 类型事件）
2. 统一四态把六态压扁（todo/doing/done→open/active/done，returned/重做语义丢失）→ stateMap 补全六态（issue.ts:77 起三张映射表合并修订）
3. 审批条目在 /issues 的点击去向是 ApprovalCenter 而非统一详情——P1 定夺：保留跳转（单据语义重）或嵌套摘要视图
4. P1 协作语义（claim/reassign/labels/blocked）两面共消费，施工时以 /issues 为主入口、TaskBoard 复用同 API 层
