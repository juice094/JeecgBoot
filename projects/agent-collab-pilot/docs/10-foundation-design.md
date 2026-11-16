# 10 — 团队效率工具底层基座设计（分工版）

> 2026-11-16。目标读者：团队成员（juice094 / Ym3699 / qingyanjunzi）。
> 定位：基座 = 所有上层应用依赖的公共能力，**不含具体业务**。业务功能全部以"基座之上的模块"形式开发。
> 原则：能组装成熟件就不重写（军规：YAGNI）。
> **⚠️ 2026-11-16 方向调整（docs/11，技术总监指示）：账号池/浏览器池/Agent 调度/WSS 网关整体转为研究线封存，不作为产品设计；产品主线 = 基础团队协作功能（见 ROADMAP M1-M4）。** 本节分层视图保留作全景参考，L5 执行按封存处理。

## 零、全局视图

```
┌─ L5 Agent 协作层 ────────────────────────────────┐
│  设备准入 / WSS 网关 / Agent 注册调度 / 浏览器池  │  ← 自研（本项目差异点）
├─ L4 团队协作层 ──────────────────────────────────┤
│  IM(Mattermost) / 文档协同(OnlyOffice) / 文件预览 │  ← 组装+二开
├─ L3 平台基座层 ──────────────────────────────────┤
│  RBAC(JeecgBoot) / SSO / 接口文档 / 审计日志      │  ← 裁剪 JeecgBoot
├─ L2 微服务控制面 ────────────────────────────────┤
│  Nacos(注册配置) / Gateway / 任务调度             │  ← 组装
├─ L1 数据底座 ────────────────────────────────────┤
│  MySQL8 / MyCat2 / Redis / MinIO / Kafka(可选)    │  ← 组装
├─ L0 运行时与网络 ────────────────────────────────┤
│  Tailscale 组网 / Docker / 出口代理(mihomo) / 入口 │  ← 组装
└──────────────────────────────────────────────────┘
```

## 一、L0 运行时与网络

| 模块 | 职责 | 选型 | 参考源码 | 状态 | 建议负责 |
|---|---|---|---|---|---|
| 设备组网 | 成员设备零入站内网互通、ACL | Tailscale | https://github.com/tailscale/tailscale | ✅ 已通（rog-x ↔ VM-14-0-ubuntu） | 宿 |
| 容器运行时 | 全栈容器化交付 | Docker + Compose | https://github.com/docker/compose | ✅ 双侧 | 全员基本功 |
| 入口代理 | 域名/证书/内网服务反代 | Nginx | https://github.com/nginx/nginx | ⬜ 未配 | — |
| 出口代理 | 成员共享代理出口 | mihomo | https://github.com/MetaCubeX/mihomo | ⚠️ 对侧骨架在、节点健康待复核 | 宿 |

## 二、L1 数据底座

| 模块 | 职责 | 选型 | 参考源码 | 状态 | 建议负责 |
|---|---|---|---|---|---|
| 关系库 | 业务数据（分片友好 schema） | MySQL 8 | https://github.com/mysql/mysql-server | ✅ 本机3306/对侧13306 | — |
| 分片中间件 | 热表水平扩展预留 | MyCat2 1.21 | https://github.com/MyCATApache/Mycat2 | ⚠️ 双侧在跑，账号/分片规则未对齐 | 成员C |
| 缓存/发号 | 会话状态、分布式锁、seq 发号 | Redis 7 | https://github.com/redis/redis | ✅ 双侧 | — |
| 对象存储 | 文件/附件（替代 jeecg 本地上传） | MinIO | https://github.com/minio/minio | ⬜ 未装 | 成员B |
| 消息队列 | 审计/消息异步解耦（后置） | Kafka | https://github.com/apache/kafka | ✅ 本机 docker 在跑 | 后置 |

## 三、L2 微服务控制面

| 模块 | 职责 | 选型 | 参考源码 | 状态 | 建议负责 |
|---|---|---|---|---|---|
| 注册中心+配置 | 服务/agent 注册发现、配置下发 | Nacos 3.x | https://github.com/alibaba/nacos | ✅ 双侧在线 | — |
| API 网关 | 统一入口、准入校验前置 | Spring Cloud Gateway | https://github.com/spring-cloud/spring-cloud-gateway | ⬜ W1 后接入 | 成员C |
| 任务调度 | 定时任务/清扫器 | XXL-Job（或 jeecg 内置 Quartz） | https://github.com/xuxueli/xxl-job | ⬜ jeecg 已内置 Quartz 可先用 | — |

## 四、L3 平台基座层（裁剪 JeecgBoot 3.9.6）

| 模块 | 职责 | 实现 | 参考源码 | 状态 | 建议负责 |
|---|---|---|---|---|---|
| RBAC/组织 | 用户/角色/菜单/租户/数据权限 | JeecgBoot 保留 sys_* | https://github.com/jeecgboot/jeecg-boot | ✅ 运行中；裁剪=W1 砍 online/报表/大屏 | 宿 |
| 统一认证 | 多系统单点登录（Jeecg/Mattermost/OnlyOffice 共用账号） | OAuth2/OIDC，候选 Keycloak | https://github.com/keycloak/keycloak | ⬜ 方案未拍板 | 成员B |
| 接口文档 | 代码即文档，团队共用入口 | Knife4j + springdoc | https://github.com/xiaoymin/knife4j · https://github.com/springdoc/springdoc-openapi | ✅ 本机 8081；对侧共享实例脚本已备（deploy-doc-vm.sh） | 宿 |
| 审计日志 | 操作审计 + 消息合规审计结果存证 | pilot 自研 audit_rules/audit_results（schema 已有） | 本仓库 schema.sql | ⬜ 未开发 | 成员B |

## 五、L4 团队协作层（组装 + 二开）

| 模块 | 职责 | 选型 | 参考源码 | 状态 | 建议负责 |
|---|---|---|---|---|---|
| IM 工作台 | 单聊/群聊/频道/语音消息/文件共享 | Mattermost Team Edition | https://github.com/mattermost/mattermost | 🔶 本机演示拉起中 | 成员B |
| 文档协同 | 多人实时同步编辑、版本历史 | OnlyOffice Document Server | https://github.com/ONLYOFFICE/DocumentServer | 🔶 本机演示拉起中 | 成员B |
| 文件预览 | 全格式在线预览 | KKFileView | https://github.com/kekingcn/kkFileView | ⬜ 未装 | 成员B |
| 语音通话 | 1v1/群组语音会议 | Jitsi Meet（或 Mattermost Calls） | https://github.com/jitsi/docker-jitsi-meet | ⬜ 未装 | 后置 |

## 六、L5 Agent 协作层（自研差异点）

| 模块 | 职责 | 实现 | 参考 | 状态 | 建议负责 |
|---|---|---|---|---|---|
| 设备准入 | 设备注册→批准→签发 token，devices/device_sessions 表已建 | 自研（jeecg 模块） | pilot docs/01 §设备准入 | ⬜ W1 | 成员C |
| WSS 网关 | connect/auth/sync/pull/ack 帧，在线状态走 Redis | 自研（Spring WebSocket） | https://github.com/spring-projects/spring-framework (websocket) | ⬜ W1 | 成员C |
| Agent 注册调度 | Nacos 临时实例 + capabilities metadata；agent_tasks 租约抢占 | 自研 + Nacos | pilot docs/04 | ⬜ W4 | 宿 |
| 浏览器执行池 | 隔离浏览器容器，人工/自动化共用 profile | 自研（复用 rog-x MAC 号池经验） | pilot docs/02 | ⬜ W4 | 宿 |

## 七、分工与节奏建议

| 成员 | 主线 | 近期任务 |
|---|---|---|
| **宿 (juice094)** | 架构裁决 + L3 裁剪 + L5 调度 | jeecg 裁剪落地（W1）；代理与组网收尾；集成验收 |
| **成员B (Ym3699)** | L4 协作层 + L3 SSO/审计 | Mattermost+OnlyOffice 部署验收；Mattermost 接 JeecgBoot 账号（SSO）；MinIO 替换本地上传 |
| **成员C (qingyanjunzi)** | L5 准入/网关 + L2 | devices/device_sessions 链路 + WSS 网关骨架（W1）；MyCat2 账号与分片规则对齐 |

节奏：以 ROADMAP M1–M4 为迭代骨架，每模块完成须过"验收三问"：① 文档（接口注解/部署手册）齐了吗 ② 对侧 VM 能复现吗 ③ 团队成员会用吗。

## 八、给各成员的开工资料

1. 本文件（docs/10）+ docs/01-04（架构与四个子系统设计）+ docs/09（技术栈实证版本）
2. 仓库：`git clone -b pilot/w1-trim https://github.com/juice094/JeecgBoot.git`（代码）；`git fetch origin pilot/design`（设计文档，本文件所在分支）
3. 环境：本机开发机要求 JDK17 / Node20+ / pnpm10+ / Docker（check_jeecgenv.py 可自检）
4. 沟通：Mattermost 演示环境就绪后，团队频道即在其中建立（吃自己的狗粮）
