# ROADMAP — 四周 MVP 切片

## W1：底座与准入
- [ ] jeecg 裁剪（砍 online/报表/大屏，保留 RBAC）
- [ ] devices / device_sessions 建表 + 设备准入链路（注册→批准→WSS 握手签发 token）
- [ ] WSS 网关骨架（connect/auth/sync/pull/ack 帧）
- [ ] WireGuard 组网执行（本 runbook 已就绪：docs/05）

## W2：消息通路
- [ ] 消息模型落地（写扩散 + inbox + Redis 发号）
- [ ] 单聊/群聊收发、离线补拉、已读回执
- [ ] 撤回/删除语义

## W3：客户端
- [ ] SQLCipher 本地库 + 审计规则引擎（关键词/正则/模式）
- [ ] 审计 HMAC 随消息上云，服务器验签
- [ ] 双面板切换 UI（工具工作台 ⇄ 聊天工作台）

## W4：Agent 闭环
- [ ] Nacos 注册（worker 临时实例 + capabilities metadata）
- [ ] agent_tasks 租约抢占 + 清扫器
- [ ] 浏览器池注册为首个 kind=browser-pool agent
- [ ] 跑通：聊天工作台 @AI账号 → 调度 → CDP 执行 → 回复回流会话

## 已完成（2026-11-13 ~ 11-14）
- [x] 架构设计冻结（docs/01–04）
- [x] 组网方案与 runbook（docs/05）
- [x] GitHub 授权：juice094（docs/06）
- [x] 全量建表语句（docs/schema.sql）

## 待确认的决策（阻塞 W1 末/W3）
1. 客户端形态：桌面端（Tauri/Electron，倾向）vs 纯 Web
2. 群聊规模与消息量级预期
3. AI 会话消息是否全部过合规审计（倾向：是，gate 在路由进账号池之前）
4. [x] ~~本仓库推送目标~~ → 已定：juice094/JeecgBoot（jeecgboot 公开 fork），设计文档推至 `pilot/design` 分支（59914fb），W1 代码分支从上游 main 切
