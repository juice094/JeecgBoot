# 04 — Agent 注册发现与任务调度

## 注册中心：Nacos

**决策（2026-11-14 修订）**：采用 Nacos 替代自研注册表。

- 业务微服务：Spring Cloud Alibaba 原生注册
- 浏览器池 worker（Python/Node）：nacos sdk 注册**临时实例**，心跳由 Nacos 托管，进程消失自动摘除
- **capabilities 放 instance metadata**：`capabilities=web-chatgpt:2,claude-web:1`，发现侧按标签过滤 + 订阅变更推送工作台
- **Nacos3 MCP registry**：MCP 类 AI 工具直接注册，工作台作为 MCP client，省一半自研协议工作量

## 任务调度：agent_tasks 租约表（MySQL，不并入 Nacos）

Nacos 不管任务语义，抢占必须自己保证：

```sql
agent_tasks (
  id varchar(36) pk,
  capability_req varchar(128),
  payload json,
  status varchar(16),           -- queued/claimed/running/done/failed
  agent_id varchar(64),
  lease_until datetime,
  result json,
  created_by varchar(36),
  created_at datetime, updated_at datetime
)
```

**原子抢占**：

```sql
UPDATE agent_tasks
SET agent_id = ?, status = 'claimed', lease_until = NOW() + INTERVAL 120 SECOND
WHERE id = ? AND status = 'queued';
-- 影响行数=1 才算抢到
```

worker 运行期心跳续租；`lease_until` 过期由清扫器重排回 `queued`。这是集群不重复执行的唯一保障，不可省略。

## 人机协作闭环

```
任务入队 → worker 抢占(某 webAI 账号) → CDP 执行
   ↓ 命中登录失效/风控/验证码
账号标记 needs_login → 释放锁 → 通知人工
   ↓ 人工经门户 VNC 重登 → 巡检恢复 healthy
任务重新入队执行
```

人工不是调度系统的例外，是状态机里的一个标准环节。

## 负载均衡（试点简版）

worker 心跳上报 `load_score`（执行中任务数/容量），调度器选 healthy 账号里负载最低的实例。不做复杂排队论，先跑起来。
