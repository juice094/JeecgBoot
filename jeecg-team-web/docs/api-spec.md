# 接口文档 — /team/task/** 任务与评论

> 提取自 `TeamTaskController` / `TeamTaskCommentController` 实际代码（jeecg-module-team），为**在役契约**。
> 前端调用封装：`src/api/team.ts`。在线文档：启动后 Knife4j `http://localhost:8080/jeecg-boot/doc.html`。

## 全局约定

- 基址：`http://localhost:8080/jeecg-boot`；统一返回 `Result<T>`：`{ success, message, code, result, timestamp }`，业务数据在 `result`。
- 分页：`pageNo`（从 1）/`pageSize`，分页响应 `result.records` / `result.total`。
- 鉴权：Shiro + JWT。`401` 未登录/过期；`403` 角色未勾选按钮权限。每个端点标注 `@RequiresPermissions`。
- 业务错误：`success=false` + `message` 中文文案（状态机拒绝、归属校验失败等），HTTP 仍 200。
- 权限模型（F-09）：admin 默认放行；普通角色按 `team:task:*` 按钮权限勾选。归属校验在 service 层（编辑/删除限创建人或管理员；流转限指派人/创建人/管理员）。

## 任务端点（TeamTaskController，`/team/task`）

### 1. 分页列表 `GET /list`
权限 `team:task:list`。实体非空字段自动等值过滤（QueryGenerator 口径）。
| 参数 | 说明 |
|---|---|
| pageNo / pageSize | 分页，默认 1/10 |
| title / status / tag / priority / assigneeId … | 可选等值过滤 |

排序：`sort_no ASC, create_time DESC`。响应 `Result<IPage<TeamTask>>`。
前端：`src/api/team.ts`（列表合同 pageNo/pageSize → records/total）。

### 2. 新增 `POST /add`
权限 `team:task:add`。Body：Task JSON。
| 字段 | 规则 |
|---|---|
| title | 必填，空 → `任务标题不能为空` |
| status | **服务端强制 todo**（建单即 done 会被覆盖） |
| priority | 缺省补「中」 |
| taskNo | 缺省时服务端 `nextTaskNo()` 生成（租户内序号，`#N` 引用） |

成功后埋时间线首条事件（create）。响应 `Result<String>`「添加成功！」。

### 3. 编辑 `PUT /edit`
权限 `team:task:edit`。Body：Task JSON（含 id）。
- 单据不存在 → `未找到对应数据`。
- **status 以库中原值回填**（防前端篡改；状态变更唯一入口是 `/status`）。
- 归属校验：限创建人或管理员，否则 service 层拒绝。

### 4. 状态流转 `PUT /status?id={id}&status={status}`
权限 `team:task:status`。目标状态限 `todo/doing/done`。
- 状态机：todo↔doing↔done 双向可达（6 种流转全合法，done 不是终态）；同状态自流转非法。
- 校验顺序：任务存在 → 存量状态合法 → 目标状态合法 → 流转合法 → 归属校验（指派人/创建人/管理员）。
- 失败文案带 `from→to` 说明。成功埋 change_status 事件。事务内完成。

### 5. 临期查询 `GET /dueSoon?days=7&scope=me`
权限 `team:task:dueSoon`。`days` 允许 0–90（越界 → `临期窗口非法`）；`scope=me` 只看指派给当前登录人的。返回未完成（≠done）且 `due_date ≤ today+days`，按 `due_date ASC, sort_no ASC`。

### 6. 看板 `GET /board`
权限 `team:task:board`。一次拉取三列：`{ "todo": [...], "doing": [...], "done": [...] }`，组内 `sort_no ASC, create_time DESC`；不分页（任务量上行后需改分页或按列拉取）。

### 7. 详情聚合 `GET /detail?id={id}`
权限 `team:task:queryById`。返回 `TeamTaskDetail`：

```json
{
  "task":     { "id": "...", "taskNo": 12, "title": "...", "status": "doing", "...": "..." },
  "comments": [ { "id": "...", "authorName": "...", "body": "Markdown 原文", "createTime": "..." } ],
  "timeline": [ { "id": "...", "action": "change_status", "fromValue": "todo", "toValue": "doing",
                  "operatorName": "...", "createTime": "..." } ]
}
```

- comments：`team_task_comment` 按 create_time 升序。
- timeline：复用 `team_audit_log`（biz_type=task, biz_id=id，create_time 升序）。
- 任务不存在 → `未找到对应数据`。

### 8. 单查 `GET /queryById?id={id}`
权限 `team:task:queryById`。返回任务主体，不存在 → `未找到对应数据`。

### 9. 删除 `DELETE /delete?id={id}`
权限 `team:task:delete`。**逻辑删**（deleted=1，对外契约不变）。限创建人或管理员。

### 10. 批量删除 `DELETE /deleteBatch?ids=a,b,c`
权限 `team:task:delete`。**先全量校验归属，任一不过整体拒绝**（避免部分删除）；存在无效 id 同样整体拒绝。

## 评论端点（TeamTaskCommentController，`/team/task/comment`）

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/list?taskId={id}` | GET | `team:task:queryById` | 按 create_time 升序，供详情页正序渲染 |
| `/add?taskId={id}` | POST | `team:task:edit` | Body `{ "body": "Markdown 原文" }`，返回完整评论对象 |

评论校验链：`taskId` 非空 → `body` 非空（`评论内容不能为空`）→ body ≤ 10000 字符（`评论内容过长`）→ 任务存在（`任务不存在`）。作者取登录态（未登录兜底 `系统`）。成功后埋 comment 事件（to_value 存评论 id），事务内完成。

**权限口径说明**：评论读走 `team:task:queryById`、写走 `team:task:edit`——评论视为「编辑任务上下文」，不单独开权限点。

## 错误码速查

| 场景 | success | message 样例 |
|---|---|---|
| 未登录/过期 | — | HTTP 401 |
| 无按钮权限 | — | HTTP 403 |
| 标题为空 | false | 任务标题不能为空 |
| 任务不存在 | false | 未找到对应数据 / 任务不存在: id=… |
| 非法目标状态 | false | 非法的目标状态: xxx（允许值 todo/doing/done） |
| 非法流转 | false | 非法状态流转: done → done（任务 id=…） |
| 越权编辑/删除 | false | （归属校验文案，service 层给出） |
| 临期窗口越界 | false | 临期窗口非法: days=99（允许 0-90） |
| 评论空/超长 | false | 评论内容不能为空 / 评论内容过长（上限 10000 字符） |
| 批量删除部分无效 | false | 批量删除存在无效任务: ids=… |
