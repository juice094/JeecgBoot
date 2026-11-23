# 后端伪代码 — 任务/评论处理流程

> 与 Java 实现的逐方法对照（真实代码：`jeecg-module-team/.../team/{controller,service/impl}`）。
> 伪代码省略类型与样板，保留**校验顺序、事务边界、埋点时机**三个容易写错的面。

## 0. 公共件

```
auditLogger.log(bizType, bizId, bizCode, action, from, to, detail):
    row = AuditLog(bizType, bizId, bizCode, action, from, to, detail, now)
    loginUser = Shiro 当前主体
    if loginUser != null:
        row.operatorId/operatorName/createBy = loginUser.*
    auditLogMapper.insert(row)          # 只追加；调用方事务内执行，随业务回滚

ownershipGuard.requireOwnerOrAdmin(createBy, 业务名):
    loginUser = 当前主体
    if loginUser == null: return        # 未登录上下文跳过（异步补偿场景）
    if loginUser.username == createBy: return
    if loginUser 拥有 admin 角色: return
    throw JeecgBootException(无权操作他人{业务名})

ownershipGuard.requireTaskOperator(assigneeId, assigneeName, createBy, id):
    同上放行口径 + 当前用户 id == assigneeId
```

## 1. 新增任务（POST /team/task/add → controller.add）

```
function add(task):
    if isBlank(task.title): return error("任务标题不能为空")        # 边界校验最先
    task.status = "todo"                       # 服务端强制：建单入口不许带状态
    task.priority = default(task.priority, "中")
    task.createBy = 登录用户名; task.createTime = now()
    if task.taskNo == null:
        task.taskNo = nextTaskNo()             # max(task_no)+1；并发靠 uk_team_task_no 兜底
    save(task)                                  # MP insert
    auditLogger.log(TASK, task.id, task.title, CREATE, null, "todo", null)
    return ok("添加成功！")
```

## 2. 编辑任务（PUT /team/task/edit → service.edit，@Transactional）

```
function edit(incoming):
    entity = getById(incoming.id)
    if entity == null: throw("未找到对应数据")
    ownershipGuard.requireOwnerOrAdmin(entity.createBy, "他人任务")   # 归属校验在状态回填前
    incoming.status = entity.status            # 状态强制回填：状态变更唯一入口是 changeStatus
    incoming.updateBy/updateTime = 登录用户/now
    updateById(incoming)
    # 注意：edit 不埋审计——编辑是覆盖式更新，事件流只记状态机与评论等离散动作
```

## 3. 状态流转（PUT /team/task/status → service.changeStatus，@Transactional）

```
function changeStatus(id, targetCode):
    task = getById(id)
    if task == null: throw("任务不存在: id=" + id)

    from = TeamTaskStatus.fromCode(task.status)
    if from == null: throw("任务状态数据异常（存量状态=" + task.status + "）")
    target = TeamTaskStatus.fromCode(targetCode)
    if target == null: throw("非法的目标状态: " + targetCode)
    if not from.canTransitTo(target):          # switch 懒判定，同状态自流转=false
        throw("非法状态流转: " + from + " → " + target)

    loginUser = 当前主体
    if loginUser != null:
        ownershipGuard.requireTaskOperator(task.assigneeId, ..., task.createBy, id)

    task.status = target.code; task.updateBy/updateTime = ...
    updateById(task)
    auditLogger.log(TASK, task.id, task.title, CHANGE_STATUS, from.code, target.code, null)
    # 全程单事务：落库与埋点同生共死
```

状态机表（`TeamTaskStatus.canTransitTo` 即全部逻辑）：

| from \ to | todo | doing | done |
|---|---|---|---|
| todo | ✗ | ✓ | ✓ |
| doing | ✓ | ✗ | ✓ |
| done | ✓（重做） | ✓（验收不通过重启） | ✗ |

## 4. 删除（DELETE /delete、/deleteBatch → service.deleteOne/deleteBatch，@Transactional）

```
function deleteOne(id):
    entity = requireTask(id)                   # 不存在即抛
    ownershipGuard.requireOwnerOrAdmin(entity.createBy, "他人任务")
    removeById(id)                             # @TableLogic → UPDATE deleted=1

function deleteBatch(ids):
    entities = listByIds(ids)
    if len(entities) != len(distinct(ids)): throw("批量删除存在无效任务")
    for e in entities:                         # 先全量校验，任一不过整体拒绝
        ownershipGuard.requireOwnerOrAdmin(e.createBy, "他人任务")
    removeByIds(ids)
```

## 5. 临期查询（GET /dueSoon）

```
function dueSoon(days, assigneeId):
    if days < 0 or days > 90: throw("临期窗口非法")          # MAX_DUE_SOON_DAYS=90
    deadline = 今天 + days（零点）
    wrapper = status != done AND due_date != null AND due_date <= deadline
              [AND assignee_id = assigneeId]                 # scope=me 时
    return list(wrapper ORDER BY due_date ASC, sort_no ASC)
```

## 6. 看板（GET /board）

```
function board():
    tasks = list(status IN (todo,doing,done) ORDER BY sort_no ASC, create_time DESC)
    return { todo: tasks.filter(status==todo),
             doing: tasks.filter(status==doing),
             done:  tasks.filter(status==done) }   # 三态外脏数据丢弃不入列
```

## 7. 详情聚合（GET /detail）

```
function detail(id):
    task = requireTask(id)
    return {
      task:     task,
      comments: comment.listByTask(id),                     # create_time ASC
      timeline: auditLog WHERE biz_type='task' AND biz_id=id
                          ORDER BY create_time ASC           # idx_audit_timeline 覆盖
    }
```

## 8. 评论（/team/task/comment/**）

```
function listByTask(taskId):
    if isBlank(taskId): throw("任务ID不能为空")
    return list(task_id = taskId ORDER BY create_time ASC)

function addComment(taskId, body):              # @Transactional
    if isBlank(taskId):    throw("任务ID不能为空")
    if isBlank(body):      throw("评论内容不能为空")
    if len(body) > 10000: throw("评论内容过长（上限 10000 字符）")
    task = teamTaskMapper.selectById(taskId)   # 直接用 Mapper：避免与 TaskService 循环依赖
    if task == null: throw("任务不存在: id=" + taskId)

    comment = Comment(taskId, body, now, 作者=登录用户[未登录兜底"系统"])
    save(comment)
    auditLogger.log(TASK, taskId, task.title, COMMENT, null, comment.id, null)
    return comment                              # 事件行只存评论 id，正文留在评论表
```

## 9. 编号生成（nextTaskNo）

```
function nextTaskNo():
    latest = SELECT * ORDER BY task_no DESC LIMIT 1
    return latest?.taskNo != null ? latest.taskNo + 1 : 1
```

已知上限（ponytail 标注）：非原子，并发建单靠 `uk_team_task_no` 唯一索引抛错兜底；试点量级够用，高并发时改号段表或 DB 序列。

## 10. 审批流（对照参考，/team/approval/**）

```
function submit(approval, steps):              # @Transactional
    生成单号 code = OA-YYYYMMDD-NNN
    落 team_approval(status=pending, current_step=1, applicant=登录人)
    落 team_approval_step 链（steps 顺序，action=wait）
    埋 audit(submit, null → 步骤快照)

function approve(id) / reject(id, comment):    # @Transactional
    approval = requireApproval(id)
    校验当前步骤 approverName == 登录人姓名 AND action=wait   # 姓名口径，试点硬编码链
    更新步骤行(action=approved/rejected, comment, acted_at=now)
    if approved 且仍有后续步骤: current_step+1, 埋 audit(approve)
    else: 主表 status=approved/rejected, 埋 audit(approve/reject)
```
