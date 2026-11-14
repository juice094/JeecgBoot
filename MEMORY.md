# MEMORY.md — 长期记忆

## 用户与协作模式

- 团队开发项目试点，本机（VM-14-0-ubuntu，腾讯云 Ubuntu 24.04 4C/7.4G）被指定为其云测试服务器/中心代理节点——涉及部署即本机执行
- **安全组由模型服务商持有，无法开放公网入站端口** → 组网唯一解 Tailscale（v1.104.7 已装，待浏览器授权）；自建 WG 方案已废弃
- 11-13 已跑通：JeecgBoot 3.9.6（/root/JeecgBoot compose）+ Mycat2（8066）+ Nacos 3.2.2（8848/9848/18080）
- Clash（mihomo）已装未配订阅；端口矩阵与运维细节见工作区内部 docs/（gitignore 排除，不入公开库）
- 协作偏好：直接给可执行 runbook + 明确决策点；技术讨论接受架构推演，但每轮要收敛到"下一步动作"
- GitHub 身份：**juice094**（gh CLI 已授权，push 前必须说明目标仓库与内容）

## 进行中项目：Agent Collab Pilot

**目标**：AI 集群团队协作效率工具（IM 工作台 + webAI 账号池 + Agent 调度），jeecg 为 RBAC 底座。

**当前状态**（2026-11-14）：架构冻结，文档与建表语句已落盘 `projects/agent-collab-pilot/`，GitHub 授权完成，待确认仓库名后推送。

**不可违背的关键决策**：
1. 消息 = 写扩散 + 每人 inbox 游标（TIM 式），离线补拉靠游标，不引消息中间件
2. 一账号一容器；人工 VNC 登录与 CDP 自动化共用同一 `--user-data-dir`，会话锁强制互斥
3. 注册中心 = Nacos（临时实例 + metadata 携带 capabilities）；任务抢占 = agent_tasks 原子 UPDATE 租约，两者绝不合并
4. 合规审计在客户端执行、HMAC 签名上云，服务器验签不验内容；audit 未过消息禁入 AI 会话
5. 内容存客户端（SQLCipher），云端只存关系/准入/审计结果
6. schema 分片友好：无 DB 自增依赖（seq 全 Redis INCR），热表显式分片键
7. 内网服务只监听 Tailscale 网卡（100.x），公网零暴露（安全组不可自管，无公网入站）
8. webAI 账号按可消耗资源设计（ToS 风险已告知用户）

**下一步**：W1——① `tailscale up` 浏览器授权完成组网（链接当日有效）② jeecg 裁剪 ③ 设备准入链路。待用户答复：客户端形态、群聊规模、审计范围。

## 教训 / 备忘

- Chrome 136+：`--remote-debugging-port` 必须配非默认 `--user-data-dir`，且与人工会话互斥（SingletonLock）——账号池设计的硬约束
- KasmVNC 系镜像协议私有，接不进经典 VNC 网关，浏览器池镜像须自研（debian + xvfb + x11vnc + chromium）
- 云服务器场景：WireGuard 通的标志是安全组放行 UDP 51820，而非系统防火墙
- Tailscale 授权链接当日有效，过期重跑 `tailscale up` 换新链接
- **git add -A 事故（11-14）**：workspace 仓库 remote 是公开 fork，-A 把系统文件推上公开分支，force-push 清理后已用 .gitignore 配置层根治（用户原则：配置大于约定）；含凭据/内部信息的文件一律走 gitignore，不靠自觉
