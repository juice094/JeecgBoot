# 07 — Jeecg 微服务价值评估与模块划分（2026-11-14）

> 触发：用户指令「挂载 jeecg 微服务节点到 nacos3，关闭不需要的微服务，仅开必要模块，对候选功能做组织与划分的价值判断」
> 结论：pilot 形态 = gateway(9999) + system-cloud(7001) 双节点注册现有 nacos 3.2.2；其余全部关闭或候选。部署文件 `/root/JeecgBoot/docker-compose-pilot.yml`。

## 评估方法

四维打分，一票否决制：

| 维度 | 含义 |
|---|---|
| **直接价值** | 对 agent 协作试点（RBAC/IM/账号池/调度）是否有不可缺的作用 |
| **架构契合** | 与已定决策（写扩散消息、DB 租约调度、不引中间件）是否冲突 |
| **成本** | 常驻内存（本机 7.4G 总盘）+ 运维面 + 攻击面 |
| **可恢复性** | 未来要用时再启的代价（配置在库/在库 nacos，重建 < 10min） |

## 模块逐一裁决

### ✅ 开启（P0，共 ~1.5G 内存上限）

| 模块 | 端口 | 价值判断 |
|---|---|---|
| `jeecg-cloud-gateway` | 9999 | **控制面门面**。统一入口，设备准入校验、路由、未来限流全挂这。微服务架构下不可缺 |
| `jeecg-system-cloud` | 7001 | **RBAC 地基**。sys_user/role/permission/字典/菜单——所有手写模块（im/device/account/audit）的权限都挂在这套体系上 |

### ❌ 关闭（与架构冲突或零价值，配置/镜像保留可重启）

| 模块 | 关闭理由 |
|---|---|
| `jeecg-demo-cloud` | 示例 CRUD，对 pilot 零价值，纯攻击面 + ~500M 内存 |
| `rabbitmq` | **架构一票否决**：消息机制已定写扩散 + inbox 游标（决策 1），引 MQ = 架构债 |
| `jeecg-cloud-sentinel` | 流量治理，试点期无流量；重启策略兜底 |
| `jeecg-cloud-monitor` | admin 监控台，docker stats + compose restart 已够 |
| `seata/shardingsphere` test 模块 | 分布式事务/分片示例；分片友好已在 schema 层走 MyCat2 路线（决策 6），不引 sharding-jdbc 双轨 |
| `jeecg-cloud-nacos`（官方构建） | 已有独立部署的 nacos 3.2.2，复用不重建 |

### ⏸ 候选（价值真实，但不在 W1-W2 关键路径）

| 模块 | 候选理由 | 启用条件 |
|---|---|---|
| `jeecg-cloud-xxljob` | 人工资任务（巡检/报表）未来可能需要；但 agent 调度已定 `agent_tasks` DB 租约（决策 3），两线不并 | 租约调度在真实负载下不够用，或出现非 agent 类定时任务需求（≥W3 再评估） |
| `pgvector` | jeecg AI 的向量库；pilot 未用 RAG/知识库，webAI 账号交互不需要向量检索 | W3-W4 若接知识库/记忆功能则启用 |
| `sharding.yaml`（nacos 配置） | 已在库，源码已注释 | MyCat2 真正接管分片时启用 |

## 候选功能的组织与划分

按**控制面 / 数据面 / 执行面**三域归位（与 01 架构图一致）：

```
控制面（谁能进、流量怎么走）
  gateway(9999) ── 设备准入校验、统一入口
  nacos(8848)   ── 服务注册 + agent registry + MCP registry
  未来: device-service（Tailscale 节点身份 + wg_pubkey 绑定）

数据面（业务状态）
  system(7001)  ── RBAC 底座
  未来手写: im / account / audit 模块（仅挂 system 菜单与权限）

执行面（跑任务的实体，全部以 agent 身份注册进 nacos）
  browser-pool 容器 ── kind=browser-pool, capabilities=[vnc,cdp,webai-login]
  worker 进程      ── kind=worker, capabilities=[task-exec,audit-check]
  MCP server      ── kind=mcp-server, capabilities=[tools...]
```

**划分判断**：控制面与数据面是 jeecg 原生资产 + 少量手写；执行面是全新自研，与 jeecg 零代码共享。三者只通过 nacos 注册发现 + REST/WSS 交互——执行面整体可平移到其他节点（架构 01 的控制面/数据面分离原则）。

## Nacos 挂载方案

- **服务发现**：gateway / system-cloud 以 ephemeral 实例注册 public 命名空间 DEFAULT_GROUP（dev profile 默认零配置生效）
- **Agent registry**：独立 group `AGENT_GROUP`，避免与 jeecg 业务服务混淆：
  - 服务名 `agent-browser-pool` / `agent-worker` / `agent-mcp-server`
  - metadata：`{"kind":"browser-pool","capabilities":"vnc,cdp","version":"0.1","node":"vm-14-0-ubuntu"}`
  - 任务发现 = 调度方查 `AGENT_GROUP` 健康实例 → 按 capabilities 匹配 → `agent_tasks` 原子抢单（决策 3，注册与调度不合并）
- **配置中心**：jeecg 业务配置在 public/DEFAULT_GROUP（已在库）；agent 侧配置（浏览器池上限、审计规则版本）后续放 `AGENT_GROUP`
- **命名空间**：试点单 public 空间，不拆 dev/prod——避免过度设计，拆分代价 = 重导配置，< 10min

## 当前部署形态（✅ 2026-11-14 22:15 已落地）

```
docker-compose-pilot.yml（本会话创建）
  jeecg-system-cloud-start  :7001  (内网)   ✅ nacos 注册 jeecg-system, healthy
  jeecg-boot-gateway        :9999  ← 团队访问入口  ✅ Up
复用 docker-compose.yml 已在跑: mysql(13306) redis nacos(8848/9848) nginx(80→9999)
已关停: jeecg-boot-system(单体,8080) / pgvector / demo·xxljob·sentinel·rabbitmq 保持关
```

**切换验证**：前端 80 → 200；网关 9999 → 200；登录请求穿透 nginx→gateway→system 返回业务响应（412 验证码错误=全链路活）；内存净省 ~700M（微服务对 1.19G 替换单体 1.3G+pgvector 0.1G，总占用 3.5G→2.8G）。

**运维纪律**：单体 compose 的 nginx 服务定义仍在原文件，勿在 pilot 期执行 `docker compose -f docker-compose.yml up`（会抢 80 端口重建旧代理）。

## 已知缺陷（jeecg 定制版 nacos 3.2.2）

- **HTTP v3 admin 注册接口不可用**：`POST /nacos/v3/admin/ns/instance` 全路径抛 `NoClassDefFoundError: org/apache/commons/lang/StringUtils`（fat jar 缺 commons-lang 2.x）。已向 BOOT-INF/lib 注入 commons-lang-2.6 并重启，错误依旧（调用方可能在隔离 classloader 或错误被缓存，未深挖）。**不影响主路径**：nacos-client SDK 走 gRPC 注册正常（jeecg-system 20s 注册成功即证据）。agent 执行层注册一律走 SDK，HTTP admin 仅作查询兜底。
- v1 OpenAPI 已 501 移除（设计如此）；查询用 `/nacos/v3/admin/ns/instance/list`。
