---
title: 任务栏与布告 Issues 体系接口文档
docId: TASK-NOTICE-ISSUE-API-001
status: active
version: 1.0.1
owner: bizflow-dev（后端）/ juice094（business 线）
updated: 2026-11-17
---

# 任务栏与布告 Issues 体系接口文档

> 本文档与已实现代码一致（jeecg-module-team，2026-11-17 落地）。布告（N 线）复用 jeecg `sys_notice` 既有接口，不在本文展开。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| REST Base Path | `/jeecg-boot/team`；下文接口表路径写相对段（如 `/team/task/list`），完整路径 = Base Path + 相对段，即 `/jeecg-boot/team/task/list` |
| 认证 | jeecg 登录态（`X-Access-Token` header，Shiro 校验） |
| 统一响应 | jeecg `Result<T>`：`{ "success": true, "code": 200, "message": "...", "result": {}, "timestamp": ... }`；业务错误 `Result.error("文案")` |
| 分页参数 | `pageNo`（默认 1）、`pageSize`（默认 10），返回 `IPage`（records/total/pages/current） |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（GMT+8）；任务/单据日期字段为 `yyyy-MM-dd` |
| 操作留痕 | 写操作带 `@AutoLog`；状态流转额外落 `team_audit_log` |

## 2. 任务接口（/team/task）

### 2.1 分页列表

| 项 | 说明 |
|---|---|
| GET | `/team/task/list?pageNo=&pageSize=&status=&tag=&priority=&assigneeId=&title=` |
| 过滤 | QueryGenerator 字段匹配；排序 sort_no asc, create_time desc |

**响应**

```json
{
  "success": true, "code": 200,
  "result": { "total": 42, "pages": 5, "current": 1, "records": [
    { "id": "t1", "title": "看板联调", "status": "doing", "tag": "开发", "priority": "高",
      "assigneeId": "u1001", "assigneeName": "张三", "dueDate": "2026-11-20", "sortNo": 3,
      "createTime": "2026-11-17 10:00:00" }
  ]}
}
```

### 2.2 新增

| 项 | 说明 |
|---|---|
| POST | `/team/task/add`，body `TeamTask`（title 必填） |
| 服务端 | status 恒为 todo（忽略客户端传值，防止建单即完成绕过状态机）；priority 缺省 中；create_by 取登录人 |

### 2.3 编辑 / 删除

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/team/task/edit` | 全字段编辑，但服务端强制回填库中原 status（编辑不改状态）；状态变更只能走 `/status` |
| DELETE | `/team/task/delete?id=` | 物理删除（试点期；逻辑删挂起待拍板） |
| DELETE | `/team/task/deleteBatch?ids=a,b` | 批量删除 |

### 2.4 状态流转

| 项 | 说明 |
|---|---|
| PUT | `/team/task/status?id=<id>&status=<todo\|doing\|done>` |
| 状态机 | todo→doing/done；doing→todo/done；done→todo/doing；自流转非法 |
| 失败 | `Result.error("非法状态流转: todo → todo（任务 id=...）")` 等带上下文文案 |

### 2.5 临期查询

| 项 | 说明 |
|---|---|
| GET | `/team/task/dueSoon?days=7&scope=me` |
| 规则 | due_date ≤ today+days 且 status≠done；days 限 0-90（越界报错）；scope 缺省不按人过滤，scope=me 仅当前登录人被指派；today 取 GMT+8；due_date 为空的任务不返回；排序 due_date asc, sort_no asc |

### 2.6 看板一次拉取

| 项 | 说明 |
|---|---|
| GET | `/team/task/board` |
| 响应 | `{ "todo": [...], "doing": [...], "done": [...] }`，组内 sort_no asc, create_time desc；不分页；未知 status 的脏数据丢弃不入任何列 |

### 2.7 详情

| 项 | 说明 |
|---|---|
| GET | `/team/task/queryById?id=` |

## 3. Issues 接口（/team/bug）

### 3.1 分页列表

| 项 | 说明 |
|---|---|
| GET | `/team/bug/list?pageNo=&pageSize=&status=&reporterId=` |
| 排序 | create_time desc |

### 3.2 提交反馈

| 项 | 说明 |
|---|---|
| POST | `/team/bug/add`，body `{ "description": "..."（必填，≤2048）, "screenshotPath": "..." }` |
| 服务端 | status 强制 open；reporter 取登录人；screenshotPath 仅预留字段，本期不提供附件上传接口 |

### 3.3 状态流转

| 项 | 说明 |
|---|---|
| PUT | `/team/bug/status?id=<id>&status=<open\|processing\|resolved\|closed>` |
| 状态机 | open→processing/closed；processing→resolved/open；resolved→closed/open；closed 终态 |
| 失败 | 非法码/非法路径返回带上下文文案（含 Bug id 与 from→to） |

### 3.4 删除 / 详情

| 方法 | 路径 | 说明 |
|---|---|---|
| DELETE | `/team/bug/delete?id=` | 物理删除（试点期） |
| DELETE | `/team/bug/deleteBatch?ids=a,b` | 批量删除 |
| GET | `/team/bug/queryById?id=` | 详情 |

## 4. 布告接口（N 线）

| 项 | 说明 |
|---|---|
| 公告 | 复用 jeecg `sys_notice` 既有接口（jeecg 公告 API），本体系不新建表、不写 sys 表 |
| 仪表盘 | team 侧三计数见申请表流转体系接口文档 §6（`/team/dashboard/summary`：pendingApprovalCount / myUnfinishedTaskCount / openBugCount）；sys 侧公告/未读聚合留 TODO，待宿裁决 |

## 5. 审计追溯（共用）

| 项 | 说明 |
|---|---|
| GET | `/team/audit/list?pageNo=&pageSize=&bizType=task\|bug&action=&bizId=` |
| 过滤 | QueryGenerator 字段匹配；排序 create_time desc |
| 用途 | 任务/Bug 状态流转留痕（biz_type=task/bug，action=change_status）；试点期所有登录用户可查，F-09 后限管理员 |

**响应示例**

```json
{
  "success": true, "code": 200,
  "result": { "total": 3, "records": [
    { "id": "a1", "bizType": "bug", "bizId": "b9", "action": "change_status",
      "fromValue": "open", "toValue": "processing", "operatorName": "张三",
      "createTime": "2026-11-17 11:00:00" }
  ]}
}
```

## 6. 错误口径

业务错误统一走 `Result.error("上下文文案")`，无 HTTP 状态码语义；典型文案：

- 任务：`任务不存在: id=...`、`非法的目标状态: xxx（允许值 todo/doing/done）`、`非法状态流转: todo → todo（任务 id=...）`（自流转/非法路径示例；todo→done 属合法流转，不报错）
- Bug：`Bug 反馈不存在: id=...`、`非法状态流转: open → resolved（Bug id=...）`
- 临期：`临期窗口非法: days=...（允许 0-90）`

## 7. 关联文档

- 需求文档：`任务栏与布告 Issues 体系需求文档.md`（同目录）
- 申请表流转体系（含仪表盘聚合）：`申请表流转体系接口文档.md`（同目录）

