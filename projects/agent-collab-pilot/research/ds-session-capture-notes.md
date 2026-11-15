# DS 会话捕获研究笔记（11-15 夜，K 云端形态）

## 已确认事实（K 记忆 + rog-x 实况）
- 号池实体：rog-x `~/.dsh/deepseek-web/` = accounts/ + login-profile/ + master/ + runs/
- dsh-ext 的 subagent-deepseek-web provider = 委派 chat.deepseek.com 网页会话（号池 UI 维护，dsh web :3080）
- MAC 与其互补：MAC 生产 token，dsh 消费 token，accounts 既有格式交接
- dsh web API 鉴权：URL `?token=` 只在 / 与 /connect 换签名 cookie，后续带 cookie（curl -c/-b）
- gray-roles：sessionId→role 存 desktop profile settings → 会话以 sessionId 可寻址
- 官方 DeepSeek Harness 客户端 0.2.0-rc.2：desktop profile，自带 web 127.0.0.1:19387，3080 独立 web 已退役
- ~/.dsh 有 sessions/ storages/ logs/ attachments/ docs/

## 待查
- [ ] conversations 物理存储位置与格式（sessions/? storages? sqlite/leveldb/jsonl）
- [ ] dsh web :19387/:3080 暴露的会话列表/历史 API
- [ ] webcode-gateway-protocol.md 契约（dsh ↔ webcode 桥）
- [ ] deepseek-web-pool-design.md 号池设计（accounts 格式）
- [ ] relay server.rs 是否可挂消息钩子（流经即落库）
