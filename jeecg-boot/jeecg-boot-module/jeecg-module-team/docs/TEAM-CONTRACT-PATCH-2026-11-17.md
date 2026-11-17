# 任务栏 / 布告 Issues 体系 —— 契约修订记录（2026-11-17）

> 对应文档：`任务栏与布告 Issues 体系接口文档.md`（TASK-NOTICE-ISSUE-API-001）、`任务栏与布告 Issues 体系需求文档.md`（TASK-NOTICE-ISSUE-REQ-001）。
> 本文记录评审发现的 P0 契约缺陷与修订口径；后端已按此对齐，原文档请按本记录合并后升版 v1.0.1。

## 1. 必须修订（P0）

### P0-1 错误文案自相矛盾
- 原文：接口文档 §6 示例 `非法状态流转: todo → done（任务 id=...）`。
- 问题：`todo → done` 是合法流转（见 §2.4 与需求 §4.3），示例自相矛盾。
- 修订：改为 `非法状态流转: todo → todo（任务 id=...）`，与需求 §9 验收第 2 条一致。

### P0-2 路径拼接歧义
- 原文：Base Path `/jeecg-boot/team`，接口表又写 `/team/task/list`。
- 问题：易读成 `/jeecg-boot/team/team/task/list`。
- 修订：统一为完整路径 `/jeecg-boot/team/task/list`，或接口表只写相对路径 `/task/list`，二选一并在全文一致。

### P0-3 编辑接口可绕过状态机（已修代码）
- 原文：§2.3 `PUT /team/task/edit` 全字段编辑，仅提示“status 变更请走 /status”。
- 问题：全字段 `updateById` 会把 body 里的 status 直写库，绕过状态机，违反需求 §4.1。
- 代码已修：`TeamTaskController.edit` 强制 `teamTask.setStatus(entity.getStatus())`，编辑不再改状态。
- 文档修订：§2.3 明确“`/edit` 忽略 status，状态变更只能走 `/status`”。

### P0-4 新增接口默认值口径（已修代码）
- 原文：§2.2 “status 缺省 todo”。
- 问题：“缺省”被理解为“客户端不传才 todo”，客户端可传 status 建单即完成。
- 代码已修：`TeamTaskController.add` 无条件 `setStatus("todo")`。
- 文档修订：§2.2 改为“status 恒为 todo，忽略客户端传值”。

### P0-5 仪表盘口径与引用不一致
- 原文：需求 T-5/I-4 随 `/team/dashboard/summary` 输出，但接口文档未定义端点与字段；需求 §10 指向申请表文档 §5，接口 §4 指向 §6，章节号冲突。
- 修订：接口文档补 `/team/dashboard/summary` 字段与口径：
  - `pendingApprovalCount`：status=pending
  - `myUnfinishedTaskCount`：assigneeId=当前登录人 且 status in (todo, doing)
  - `openBugCount`：status in (open, processing, resolved)
  - 统一引用章节号。

## 2. 建议补充（P1）

1. 看板脏状态处理：`/board` 对未知 status 丢弃并记日志（当前 `TeamTaskBoard.of` 已丢弃，文档补一句）。
2. Bug 校验：`description` 必填且 ≤2048，接口补长度校验口径。
3. `screenshotPath` 与“本期不含附件上传”矛盾：注明“仅预留字段，不提供上传接口”。
4. 枚举校验：`tag`/`priority`/`status` 非法值应拒绝，补默认值与 `update_by` 回填说明。
5. 临期查询：`scope` 缺省值、允许值、`due_date` 为空处理、`today` 时区（GMT+8）补全。
6. 审计：补“状态流转与审计同事务”，查询权限（管理员/登录用户）明确。
7. 分页示例补 `pages`/`current`。
8. 权限：试点期所有登录用户可访问，F-09 后补 `team:task:*` / `team:bug:*`，文档显式标注风险与计划。

## 3. 本次后端对齐改动

| 文件 | 改动 | 目的 |
|---|---|---|
| `TeamTaskServiceImpl.changeStatus` | 补 `@Transactional(rollbackFor = Exception.class)` | 状态与审计同事务，随业务回滚 |
| `TeamBugServiceImpl.changeStatus` | 同上 | 同上 |
| `TeamTaskController.add` | 无条件 `setStatus("todo")` | 防建单即完成绕过状态机 |
| `TeamTaskController.edit` | 用库中原状态回填 | 防编辑绕过状态机 |

## 4. 验证与残余风险

- 已过：`mvn -o compile`、`mvn -o test-compile`；既有三份纯 Java 自测（12/15/10 断言）。
- 新增单测：任务/Bug 状态机（含自流转拒绝、open→resolved 拒绝、closed 终态）、控制器状态兜底、`@Transactional` 注解契约。
- 未覆盖：真实数据库事务回滚（需 Spring 集成测试 + MySQL）、HTTP 层鉴权与序列化。

## 5. 关于“参考 GitHub Issues 复刻”的评估

- 合适的部分：状态机 + 只追加审计 + 显式流转错误，与 Issue 工作流（open/processing/resolved/closed）语义接近，团队易理解，试点期成本低。
- 需注意的部分：GitHub Issues 是面向开源协作的通用模型，团队 OA 还需**权限边界、审批链、多租户、字段级校验、统计聚合**，不能只照搬状态枚举；当前 `handler_name` 自由文本、无认领端点、物理删除即为短板。
- 建议：保留 Issues 状态语义作为 Issues 线，但把“指派/认领、评论、关联单据、软删除、通知”作为二期补齐，才具备团队 OA 的通用性与实用价值。
