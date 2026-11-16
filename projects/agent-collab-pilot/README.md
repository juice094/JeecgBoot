# Agent Collab Pilot — AI 集群团队协作效率工具（试点）

> 阶段：架构设计冻结，W1 实施中（2026-11-16 起开发主阵地移至 rog-x/Win11，云 VM 为对侧节点）
> 部署目标机：rog-x（Win11，开发机）+ VM-14-0-ubuntu（云，对侧节点）
> 定位（2026-11-16 技术总监指示，docs/11）：**正向道路的团队协作效率工具**——先完成基础功能（IM/文档协同/任务/日程/公告/审批/文件）；账号池等"那一套"封存为研究线，不作为产品设计

## 一句话定义

以 jeecg-boot 为 RBAC 底座，构建 **IM 工作台（微信/TIM 式消息机制）+ 账号池执行层（隔离浏览器登录 webAI）+ Agent 注册调度（Nacos）** 的一体化团队协作系统，云端存用户关系与设备准入，客户端存会话内容并前置合规审计。

## 目录导航

| 文档 | 内容 |
|---|---|
| [docs/01-architecture.md](docs/01-architecture.md) | 总体分层、jeecg 删改清单、部署视图 |
| [docs/02-account-pool-and-browser.md](docs/02-account-pool-and-browser.md) | 隔离浏览器池、账号生命周期、人机会话锁 |
| [docs/03-im-chat-and-sync.md](docs/03-im-chat-and-sync.md) | 消息模型、多端同步协议、合规审计 |
| [docs/04-agent-registry-and-tasks.md](docs/04-agent-registry-and-tasks.md) | Nacos 注册发现、任务租约调度 |
| [docs/05-network-tailscale.md](docs/05-network-tailscale.md) | 团队设备组网 runbook |
| [docs/09-tech-stack-reality.md](docs/09-tech-stack-reality.md) | 技术栈选型现状（实证版本，2026-11-16 rog-x 盘点） |
| [docs/10-foundation-design.md](docs/10-foundation-design.md) | 底层基座设计（分工版，含参考源码与成员任务） |
| [docs/11-direction-pivot-2026-11-16.md](docs/11-direction-pivot-2026-11-16.md) | **方向调整存档**：基础功能为主，池号线封存（技术总监指示） |
| [docs/06-github-auth-setup.md](docs/06-github-auth-setup.md) | GitHub 授权（已完成） |
| [docs/schema.sql](docs/schema.sql) | 全量建表语句（MySQL 8，分片友好） |
| [ROADMAP.md](ROADMAP.md) | 四周 MVP 切片与当前状态 |

## 关键决策速览

1. **组网**：Tailscale（安全组由服务商持有、不可开公网端口 → 打洞/DERP 零入站；v1.104.7 已装待授权），内网 100.x
2. **注册中心**：Nacos（临时实例 + metadata 携带 capabilities；Nacos3 MCP registry 承接 MCP 类工具）
3. **任务调度**：`agent_tasks` 表 + 原子抢占租约（MySQL，不并入 Nacos）
4. **消息机制**：写扩散 + 每人 inbox 独立游标（TIM 式），离线补拉靠游标
5. **数据库**：试点单机 MySQL 8；schema 按 MyCat2 分片友好设计（热表显式分片键，无 DB 自增依赖，seq 走 Redis）
6. **内容边界**：消息内容存客户端（SQLCipher），云端存关系/准入/审计结果（HMAC 签名防绕过）
7. **人机协作原语**：每账号一个容器，人工 VNC 登录与自动化 CDP 共用同一 profile，会话锁保证互斥
