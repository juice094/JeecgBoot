# 08 · DS 会话捕获机制研究 + session-ingest 微服务设计

> 状态：研究报告 v1（2026-11-15 夜，K/云端）
> 上游：docs/05-架构冻结.md（决策 1-8）、MAC 工程书（仅 rog-x）、号池设计草案 2026-10-29
> 任务来源：宿 11-15 22:18 夜间工作令——「先研究如何获取到 ds 会话，或消息经由代理机制存储成员会话数据的微服务模块」

## 一、侦察结论（全部实测/文档证实）

### 1.1 号池就是会话入口
- 号池实体：rog-x `~/.dsh/deepseek-web/`，master（源 profile，不参与调度）+ accounts/acc-<id>（每账号独立 `--user-data-dir`）+ accounts.json（注册表：id/label/profileDir/deviceId/credentialRef/enabled/lastUsedAt/stats{runs,failures}）
- 登录态 = chat.deepseek.com 的 **localStorage userToken**（非 cookie，10-29 实测）；与官方 platform.deepseek.com OAuth 是**两个认证域，凭证不可互換**
- dsh-ext 的 `subagent-deepseek-web` provider 委派网页会话给 subagent（textarea.ds-scroll-area 选择器驱动）；**runs/*.jsonl + index.json 是 agent 运行日志**（status/exitCode/sizeBytes），**不是聊天会话内容**
- 会话内容本体在 DeepSeek 服务端，按账号登录态隔离；单账号可并发多会话（10-29 实测 3.9s 双会话并行）

### 1.2 关键推论
要"获取 DS 会话"，没有现成的本地全量副本可搬——必须从链路上截获。捕获点只有四个：

## 二、捕获路线对比

| 路线 | 机理 | 保真度 | 工程量 | 风险 |
|---|---|---|---|---|
| **A. 页面内钩子** | 向 acc profile 注入 content script，包装页内 fetch/XHR，消息进出即 postMessage 落本地 | ★★★ 全文双向 | 中（选择器/dsh 插件体系已有先例 gray-roles） | DeepSeek 前端改版要跟进；页面 CSP 宽松可注 |
| **B. CDP 网络域旁路** | 池浏览器走 CDP，Network 域截 chat.deepseek.com 的 /api/v0/* 响应 | ★★★ 全文双向 | 中 | 与人工登录的 SingletonLock 互斥（决策 2 已冻结此约束）；stream 重组麻烦 |
| **C. Web API 轮询** | 用各账号 userToken 直接轮询官方 web API（chat_session fetch_page / chat history） | ★★ 服务端真相 | 低-中 | API 面非官方、未实测（**今晚未做**）；有触发风控的可能（号池红线：批量触发说明登录模式已被识别，需 spaced priming） |
| **D. dsh 工具边界钩子** | 挂 subagent_web 工具调用出入参 | ★ agent 会话 only | 低 | **捕不到成员手动聊天**——而手动聊天恰是协作消息的主体，弃 |

**推荐：A 为主 + C 为辅**。A 覆盖双向全文且天然携带账号身份（哪个 acc profile 就哪个号）；C 做冷启动回填（历史会话）与 A 的断线对账。B 留作 A 失效时的备胎。D 弃。

## 三、session-ingest 微服务设计（云端模块 M1）

### 3.1 定位
消息管道的进水口：接收各捕获端（rog-x 号池、成员客户端、MAC 池）推送的会话数据，落库、索引、供 IM 工作台查询。nacos 注册，mycat2 分片。

### 3.2 数据流
```
acc profile 浏览器（页面钩子 A）
  └─→ capture-agent（rog-x 常驻小进程，攒批+HMAC 签名）
        └─→ Tailscale ──→ session-ingest（云端，:7002）
              ├─→ nacos 注册（临时实例，metadata: capabilities=[session-ingest]）
              ├─→ 验签 → 落库（mycat2 8066 → session 库）
              └─→ 成员/会话关系同步（IM 工作台消费）
```

### 3.3 API 契约（v1 草稿）
- `POST /v1/batches` — 批量上报 `{batchId, agentId, accountRef, msgs:[{msgId, sessionId, direction, role, content, ts}]}`，幂等键 batchId+msgId
- `GET /v1/sessions?member=&account=&since=` — 会话游标拉取（写扩散 inbox 游标同构，决策 1）
- `GET /v1/sessions/{id}/msgs?cursor=` — 消息分页
- `POST /v1/backfill` — C 路线历史回填（任务化，单账号串行 spaced）

### 3.4 表草稿（走 mycat2 分片，分片键 member_id）
- `ds_sessions`（session_id, member_id, account_ref, title, source, first_at, last_at, msg_count）
- `ds_msgs`（msg_id, session_id, direction, role, content, ts, audit_status）——**内容是否上云见 §四决策修订**
- `ds_accounts_map`（account_ref, account_label, status）——号池注册表的云端镜像（不存凭证）

### 3.5 红线继承
- 号池红线：单号批量触发风控→ backfill 必须 spaced priming（经验 109/110 体系）
- 凭证永不上云：account_ref 只做哈希映射，accounts.json 本体留 rog-x
- 捕获端崩溃零数据丢失：本地先落 sqlite 队列，确认 200 才删

## 四、⚠️ 决策修订申请（需宿拍板）

**决策 5（11-14 冻结）：内容存客户端（SQLCipher），云端只存关系/准入/审计结果。**
宿 11-15 新指示：「消息经由代理机制**存储成员会话数据**的微服务模块」——按字面=消息内容上云。
两读法：
1. **全量上云**：ds_msgs 存全文 → IM 工作台跨端全文检索/复用最简单；但号池 ToS 风险与隐私面扩大，审计压力在云端
2. **元数据+引用上云，全文留客户端**：云端存会话索引+审计结果，工作台要全文时回源客户端拉 → 合规面最小，代价是成员离线时查不到全文

**默认按指示 1（全量上云，但 audit_status 字段预留客户端合规审计回写）设计**；宿一句话即可切 2。已冻结的决策 4（审计客户端执行、HMAC 上云）**不变**，两条读法都兼容。

## 五、下一步（W1.5 波次建议）
1. 宿拍板 §四（1 或 2）
2. A 路线 PoC：一个 acc profile 注入钩子，抓单会话双向消息落 jsonl（不动 dsh 本体，旁路验证）
3. session-ingest 脚手架：nacos 注册 + /v1/batches 落 mysql（复用 jeecg-pilot 基础设施）
4. C 路线 web API 面侦察（单独低风控节奏，先 1 个号手工 curl 摸端点）
