# 09 — 技术栈选型现状（实证版）

> 2026-11-16 于 rog-x（Win11）实证盘点。版本号全部来自运行中实例或构建产物，非文档转抄。
> 定位修正：本项目**不是"试点玩具"**。"试点"仅指协作规模小（3 人内）。"试点"不降低工程质量目标——本质是完成一套**成熟的微服务体系模块**：控制面 / 数据面 / 执行面分层完整，可平移、可扩展、可交付。

## 一、平台底座

| 组件 | 选型 | 实证版本 | 角色 | 状态 |
|---|---|---|---|---|
| JeecgBoot | fork `juice094/JeecgBoot`（上游 jeecgboot/JeecgBoot） | 3.9.6（pom `revision`） | RBAC 底座 + 业务载体 | ✅ 本机构建运行（`main` + 分支 `pilot/w1-trim`） |
| Spring Boot | 官方 parent | 4.1.0（pom `<version>4.1.0</version>`） | 服务框架 | ✅ |
| Java | Eclipse Adoptium JDK | 17.0.12（Maven JAVA_HOME） | 运行时 | ✅ 系统默认 java 为 26，**构建/运行必须用 JDK 17** |
| MyBatis-Plus | — | 3.5.16 | ORM | ✅ |
| Shiro + JWT | — | Shiro 3.0.0 + JWT 4.5.0 | 鉴权 | ✅ |
| 接口文档 | Knife4j | 4.5.0 | OpenAPI | ✅ `/jeecg-boot/doc.html` |

## 二、前端

| 组件 | 实证版本 | 状态 |
|---|---|---|
| Vue | 3.5.39 | ✅ |
| Vite | 8.1.4 | ✅ dev server 3100 |
| Ant Design Vue | 4.2.6 | ✅ |
| Node / pnpm | 24.17.0 / 11.3.0 | ✅ |

## 三、控制面 / 数据面 / 执行面

| 组件 | 版本（实证） | 部署 | 状态 |
|---|---|---|---|
| Nacos（注册中心） | 3.x（`nacos-server:latest`，2026-10-28 拉取） | 本机 docker `:8848`；对侧 3.2.2 `:8848` | ✅ 双侧在线 |
| MySQL | 8.0.46（本机服务）；8.0（对侧/本机 docker 集群 3307-3310） | 本机 3306 / 对侧 13306 | ✅ |
| MyCat2（分片入口） | 1.21 | 本机 docker `:8066`；对侧 `:8066` | ⚠️ 本机 root/root 未对齐，分片联调前需配账号 |
| Redis | 7.4.12（docker `redis-dev`，本机/对侧复用镜像约定） | 本机 6379；对侧 6379 | ✅ PONG |
| 隔离浏览器池（执行面） | 容器化（W4 接入） | 未建 | ⬜ W4 |
| Docker Engine | 29.9.3（Docker Desktop Win11） | 本机 | ✅ |

## 四、网络

| 组件 | 版本 | 状态 |
|---|---|---|
| Tailscale | 1.104.7（rog-x）；对侧 v1.104.x | ✅ tailnet 内 rog-x ↔ vm-14-0-ubuntu 互通（SSH/服务访问均走 100.x） |
| mihomo（出口代理） | 对侧 systemd | ⚠️ 组件在跑（7890/9090/1053），节点健康曾受 GLaDOS 订阅阻塞；对侧 11-16 记忆称已解阻塞，**本侧未复核** |

## 五、被实证修正的"试点假设"（不能全信旧文档的点）

1. **会话采集端必须在成员侧**：云服务器 IP 被 chat.deepseek.com 的 CloudFront 边缘 403（DC IP 进 WAF 黑名单）→ docs/08 §六已修，云端只做接收/存储/查询。
2. **8080 不可用于 jeecg**：本机 8080 被 nacos 容器占用（且 Docker Desktop 端口转发在 netstat 中**不显示 LISTENING**，排查时勿据 netstat 下结论）。本机约定 jeecg = **8081**（`.env.development` 已改）。
3. **MySQL 入口不统一**：jeecg 业务库直连 3306；试点 11 表落在独立库 `agent_collab`；分片走 MyCat2 8066（账号待对齐）。三线并存是现状，非疏漏。
4. **对侧 SSH 公钥授权未完成**：rog-x 的 `id_ed25519` 未被对侧 authorized_keys 接受（表现为 Server accepts key → Permission denied）。当前不阻塞（资料同步走 GitHub），但 runbook（docs/05）落地前必须解。
5. **repo-watch 的"未推送"备注会过期**：以 `git log origin/pilot/design` 为准，勿信 state 文件里的滞后描述。

## 六、成熟度判定（对照"成熟微服务体系模块"标准）

已达生产可用：RBAC 底座、注册中心、数据库（含分片预留 schema）、组网、CI 哨兵（repo-poll）。
待补才能成为"完整模块"：jeecg 裁剪落地（W1）、设备准入 + WSS 网关（W1/W2）、消息通路（W2）、审计与客户端（W3）、浏览器池注册闭环（W4）。
