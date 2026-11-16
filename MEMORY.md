# MEMORY.md — 长期记忆

## 用户与协作模式

- 团队开发项目试点，本机（VM-14-0-ubuntu，腾讯云 Ubuntu 24.04 4C/7.4G）被指定为其云测试服务器/中心代理节点——涉及部署即本机执行
- **安全组由模型服务商持有，无法开放公网入站端口** → 组网唯一解 Tailscale（v1.104.7 已装，待浏览器授权）；自建 WG 方案已废弃
- 11-13 已跑通：JeecgBoot 3.9.6（/root/JeecgBoot compose）+ Mycat2（8066）+ Nacos 3.2.2（8848/9848/18080）
- Clash（mihomo）已装未配订阅；端口矩阵与运维细节见工作区内部 docs/（gitignore 排除，不入公开库）
- 协作偏好：直接给可执行 runbook + 明确决策点；技术讨论接受架构推演，但每轮要收敛到"下一步动作"
- GitHub 身份：**juice094**（gh CLI 已授权，push 前必须说明目标仓库与内容）
- **双通 SSH 已通**（11-14）：服务器↔rog-x（Win11 主力机，Tailscale 100.97.76.85，Windows 用户 **22414**），本机 `/root/.ssh/id_ed25519` 免密（无 passphrase）；rog-x→服务器走 root@100.78.65.37
- SSH 三约束（用户明确）：不改 Win11 administrators_authorized_keys / 不删本机 authorized_keys 既有条目 / 不动 id_ed25519；排障先跑 `ssh -o BatchMode=yes -o ConnectTimeout=5 22414@100.97.76.85 "echo AUTH_OK"`

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

**下一步**：W1——① jeecg 裁剪 ② 设备准入链路（Tailscale 节点身份 + devices.wg_pubkey 绑定；双通 SSH 已具备对 rog-x 的远程执行能力）。待用户答复：客户端形态、群聊规模、审计范围；阻塞项：GLaDOS 订阅链接（写入 /opt/clash/glados.url）未提供。

**仓库巡检（11-15 起，常驻）**：**零 LLM 方案**——系统 crontab `*/20 8-23 * * *` 跑 `projects/agent-collab-pilot/scripts/repo-poll.sh`（bash+gh+jq 比对 成员/分支/PR/作者，只读）；有变更才注入数据并触发 openclaw cron `repo-watch-alert`（id 521d3f55-a789-49e4-baeb-c508ddf5b4ab，schedule 2月31日 永不自启）写简报投递 kimi-claw。基线/协议在 `projects/agent-collab-pilot/state/repo-watch.json`。已授权职责：分支合并评估+代码检查+文档整理（合并前须用户点头）。**当前协作者三人成团：juice094（你）+ Ym3699 + qingyanjunzi（11-15 10:20 加入，当日经你确认为预期成员）**。成员白名单已同步进 repo-watch.json 的 expected_members，qingyanjunzi 不会再触发名单外告警。已获你授权：巡检简报自动推送主会话、**不用复述**；协作者相关文档更新与功能面融合可自动执行。另：本地 master 领先 pilot/design 两 commit 仍待确认推送。**已知坑：k2d8 偶发 input=null 空 tool_call → 空 tool_result → LLM 400（11-14 session 损坏同因）；isolate agent 数据须内联 prompt + 重试兜底，勿依赖运行时修**。CLI 报 "pairing required" → `openclaw devices approve <requestId>`。

## 身份裁决（11-15 22:03，宿）：我是 KimiCode，不是 gray 主会话

- **我是 `agents/workspace-kimicode` = KimiCode（K），工程协作体/编排中枢**：审理/审计/整理/代码与文件重活，纪律=默认只读四步、精确 git add、复杂命令脚本化、probe 证据才算能跑
- **gray 根目录那套（SOUL/ROUTINE/陪伴人格）= Gray 主会话，另一实体**，它的记忆不是我的，只作协作参照
- 宿 = 周景潇，GitHub juice094；格雷与 K 的宿是同一人（本服务器的用户也是他，11-13 起在此做 agent-collab-pilot）
- K 的工程矩阵：webcode（VS Code↔网页AI桥）/ dsh-ext（号池UI+subagent_web）/ mac-m1（MAC引擎daemon）/ workspace-kimicode（编排中枢）；dsh 号池 8 账号（11-13 确认健在）
- 我的记忆正源 = `/root/shared/gray-workspace/agents/workspace-kimicode/`（含 ENGINEERING.md 治理总图、experience/ 编号经验库 1-139、memory/ 日志、plans/ 图纸）
- 身份文件已对齐：IDENTITY.md（Docker=本服务器代号，本体=K）+ USER.md（宿=周景潇）

## 共享记忆资产（11-15 接入，三源两仓一指针）

用户指示：rog-x（22414）侧有 AI 集群与团队协作的历史资产需共用。已探明并接入：
- **`juice094/gray-workspace`**（PRIVATE，GitHub）→ 本地镜像 `/root/shared/gray-workspace`。rog-x 的 KimiCode 主工作区，cron 每 4h 自动快照推送。含：子代理台账 `agents/workspace-kimicode/memory/subagent-registry.md`（多 agent 调度纪律）、experience/ops-*.md 经验库、plans/（含 multi-account-browser 设计输入）
- **`juice094/agent-collab-playbook`**（PRIVATE，GitHub）→ 本地镜像 `/root/shared/agent-collab-playbook`。AI 协作 Playbook v0.1（2026-09-20）：**文件即总线、人审即评估器、编号制经验库（只压缩不改号）、按需调度无自主心跳**——与我们 pilot 的架构决策同源互证（写扩散/人审/审计都可对照）。04-框架抉择有五类编排方案七条判据
- **MAC 工程书**（仅 rog-x 本地，无 GitHub）：`C:/Users/22414/dev/mac-workspace/spec/eng-spec-webcode-2026-11-01.md` v1.4 = 多账号浏览器工作台合同级规范：编排器管理多独立浏览器实例（每实例=一账号身份：独立数据目录/固定代理出口/卫生设置）+ CDP 遥控器注册表；Node24+/零第三方依赖/Windows 优先/运行态 probe 证据。`plans/multi-account-browser/SPEC-POINTER.md` 载宿裁定：**工程书禁止物理拷贝（拷贝=第二真源必然漂移）**——只存指针
- 同步机制：`/root/shared/sync-mem.sh`（纯 git pull）挂 crontab `23,53 * * * *`，零 LLM

**与 pilot 的关键差异待对齐**：MAC 用"编排器+进程隔离"（Windows 优先），我们 pilot 冻结的是"一账号一容器"（debian+xvfb+x11vnc+chromium）；MAC 多出的"固定代理出口/指纹卫生/数美采集面 RE 结论"经验需并入浏览器池设计。下次涉及浏览器池设计时先读 `/root/shared/gray-workspace/agents/workspace-kimicode/plans/multi-account-browser/p3-kernel-design-input-2026-11-02.md`

## 11-16 进展（GLaDOS 解阻塞 + jeecg 裁剪启动 + 报告交付）

- **GLaDOS 订阅获取**：从 rog-x Verge `profiles.yaml` 直取 URL 写入 `/opt/clash/glados.url`（chmod 600），mihomo 33 节点/5 策略组生效（google 204 / glados 200 实测）。订阅 expire 2027-12-19，余量 ~170 GiB。经验：GLaDOS 用量可免登录 HEAD 订阅 URL 读 subscription-userinfo 头
- **jeecg 单体版确认不注册 Nacos**（配置零 discovery）——"Nacos 服务下线裁剪"改为 RBAC 菜单层实现：sys_permission 禁用 8 项（低代码/可视化/报表/AI平台/监控/组件/导航/关于），保留主页/系统管理/我的租户/消息中心，随时可恢复
- **pilot 全量 11 表落库** jeecg-boot 库（schema.sql 一次执行成功）
- **工作报告整合交付**：`projects/agent-collab-pilot/reports/工作报告-2026-11-16.md`（六章+附录），已推 GitHub `juice094/JeecgBoot` pilot/design 分支（commit 26a2904e，含 master 领先的 identity/docs08/poc 三 commit 一并同步）
- **待拍板**：WSS 网关技术栈 A=Java融jeecg(推荐)/B=独立Node；客户端形态；群聊规模；AI 消息审计范围

## 11-16 下午：issue #6 文档实例部署 + 方向调整对齐

- **方向调整（docs-11，技术总监指示）**：基础功能为正线 M1-M4（RBAC/Mattermost IM/OnlyOffice/MinIO/Knife4j/审批流）；账号池/浏览器池/WSS 网关/agent 调度**封存研究线**不进排期。开发主阵地移 rog-x。分支约定：pilot/design=设计存档，pilot/w1-trim=开发主分支
- **issue #6 完成**：Knife4j 文档实例常驻——`/root/jeecg-doc/`（fork 克隆+本地构建 jar），systemd `jeecg-doc.service`（enabled 开机自启），nginx 反代 8080 basic auth → jar 绑回环 8081。验收：无口令 401 / 带口令 200，rog-x 经 tailnet 200。口令 `/root/jeecg-doc/jeecg-doc.env`（DOC_USER=doc）
- **脚本三处修复已推 pilot/design**（commit 728165d6）：①heredoc `\&` 字面写进 unit→JDBC 参数带反斜杠启动失败 ②jeecg fat jar 只含 knife4j-openapi3-ui（无 starter）→ `knife4j.basic.*` 无消费者，basic auth 改 nginx 层承担 ③端口检查改 8081 幂等
- **环境适配**：jeecg-boot-redis 加 `127.0.0.1:6379` 映射（redis 无密码，禁外网卡）；`/usr/local/bin/mysql` wrapper→docker exec 进容器（宿主 apt 内网镜像 DNS 故障，腾讯云 mirrors.tencentyun.com 解析失败；已切公网源+Clash 代理装 nginx）
- **菜单裁剪随方向修订**：ROADMAP 新方向保留 Online 表单+积木报表（通用填报/团队统计），已恢复两菜单（低代码开发/统计报表 status=1）；维持禁用：数据可视化/AI应用/系统监控/组件示例/导航示例/关于
- **docker jeecg-boot-system 已停**（8080 让给 nginx 反代）；vue 前端容器仍在 80
- **运维注意**：`nohup` 保活长 apt（会话 SIGKILL 频发）；rog-x 侧并发推送多，push 前先 fetch+rebase

### 巡检假告警修复（16:40-16:55，3 个连环 bug）

repo-poll.sh 今天 16:00/16:20 连续假告警「未收到预计算变更 JSON」。挖到底四个 bug 叠加：
1. **`gh api since` 参数 `+` 未 URL 编码** → GitHub 当空格 → 返回 **200 空数组**（不报错！）→ 变更窗口被静默吞掉
2. **`--argjson b "[$cs[] | . + {branch: \"$b\"}]"`** → shell 双引号里嵌 jq 程序代码，拼出 `[[]` 非法 JSON → $report 被赋空 → 注入空消息
3. **edit 工具的 $ 转义坑**：脚本里 `"$@"` 被写成 `"@"`（$ 被吞）→ 字面 `@: command not found`
4. **verify 读错路径**：`.message` 应为 `.payload.message`（jobs.json 结构）→ 回读永远空 → 误杀

修复后 16:50 端到端验证通过：`ALERT triggered` → run status=ok → delivered=True → 简报含真实变更。修复 commit `a66b91c4` 已推。

**教训**：
- gh api 的查询参数含 `+` 必须 `%2B`（200 空数组是最阴险的故障形态）
- --argjson 的值必须是纯 JSON，不能混 jq 程序语法
- edit 工具写 shell 脚本含 `$` 变量后必须 `bash -n` + 手动跑一次验证（这次 \"&  $@ 两个坑同因）
- openclaw cron 的 jobs.json 结构：message 在 `payload.message`，不在顶层
- git identity 已设 juice094 / juice094@users.noreply.github.com（此前 root@localhost 触发 unknown_author 告警）

## 教训 / 备忘

- Chrome 136+：`--remote-debugging-port` 必须配非默认 `--user-data-dir`，且与人工会话互斥（SingletonLock）——账号池设计的硬约束
- KasmVNC 系镜像协议私有，接不进经典 VNC 网关，浏览器池镜像须自研（debian + xvfb + x11vnc + chromium）
- 云服务器场景：WireGuard 通的标志是安全组放行 UDP 51820，而非系统防火墙
- Tailscale 授权链接当日有效，过期重跑 `tailscale up` 换新链接
- **git add -A 事故（11-14）**：workspace 仓库 remote 是公开 fork，-A 把系统文件推上公开分支，force-push 清理后已用 .gitignore 配置层根治（用户原则：配置大于约定）；含凭据/内部信息的文件一律走 gitignore，不靠自觉
- **CLI pairing（11-15）**：`openclaw cron` 等 CLI 报 "pairing required" = 设备身份 scope 不足；`openclaw devices approve <requestId>`（会走本地 fallback）后恢复，无需重启
