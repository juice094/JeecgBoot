# MEMORY.md — 长期记忆

## 用户与协作模式

- 团队开发项目试点，本机（VM-14-0-ubuntu）被指定为其云测试服务器/中心代理节点——涉及部署即本机执行
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
7. 内网服务只监听 WireGuard 网卡（10.10.0.1），公网零暴露
8. webAI 账号按可消耗资源设计（ToS 风险已告知用户）

**下一步**：W1（jeecg 裁剪 + WireGuard 组网执行 + 设备准入链路）。待用户答复：客户端形态、群聊规模、审计范围、仓库名与可见性。

## 教训 / 备忘

- Chrome 136+：`--remote-debugging-port` 必须配非默认 `--user-data-dir`，且与人工会话互斥（SingletonLock）——账号池设计的硬约束
- KasmVNC 系镜像协议私有，接不进经典 VNC 网关，浏览器池镜像须自研（debian + xvfb + x11vnc + chromium）
- 云服务器场景：WireGuard 通的标志是安全组放行 UDP 51820，而非系统防火墙
