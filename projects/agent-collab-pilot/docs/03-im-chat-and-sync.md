# 03 — IM 消息机制与多端同步

## 数据模型（TIM 式：写扩散 + 每人独立游标）

```sql
conversations  (id, type[single/group], name, last_msg_id, updated_at)
conv_members   (conv_id, user_id, last_read_seq, pinned, muted, joined_at)
messages       (id uuid, conv_id, conv_seq, sender_id, type,
                content_ref, audit_status, recalled, created_at)
inbox          (user_id, seq, msg_id, conv_id, created_at, deleted)  -- 每人一份
devices        (id, user_id, fingerprint, pubkey, wg_pubkey, assigned_ip,
                name, status, first_seen, last_seen, approved_by)
device_sessions(token, device_id, user_id, issued_at, expires_at, revoked)
```

群消息**写扩散**：发一条 = messages 一行 + 每个成员的 inbox 一行（各自 seq 由 Redis `INCR user:{id}:seq` 发号）。试点规模（几十人群、日万条内）单机 MySQL 无压力。

## 同步协议（WSS 帧）

```
client → server: connect {device_token}
server → client: hello {user_seq: 当前最新}
client → server: sync {since: 本设备 last_seq}          -- 补漏
server → client: batch {msgs[]} + 新游标
此后 server 实时 push {msg}; 客户端按 uuid 幂等去重
```

- **多端同步**：每设备记 `last_sync_seq`，重连先拉后推
- **已读回执**：`conv_members.last_read_seq` 单调推进，天然支持群聊部分已读
- **撤回**：tombstone 标记 + 内容清空 + 推送更新
- **删除**：仅本端墓碑（inbox.deleted），服务器漫游不受影响（微信同语义）
- **离线消息**：游标之后的 inbox 行即离线队列，不引单独中间件
- Redis 职责只有两个：在线状态、seq 发号

## 设备准入（类微信"登录设备管理"）

1. 客户端生成密钥对 + 指纹 + Tailscale 节点公钥，申请注册 → `pending`
2. 管理员工作台批准（试点可自动批准）
3. WSS 握手：证书挑战 → 校验 sys_user 凭据 + device 状态 → 签发 user+device 绑定 token
4. 吊销即断连；设备列表页可远程踢出
5. **网络层**（tailnet ACL 按用户/节点粗粒度准入）与**应用层**（握手细校验）各管各的

## 合规审计（客户端执行，签名防绕过）

```sql
audit_rules    (id, scope[chat/ai/tool], type[keyword/regex/model],
                pattern, action[block/flag], enabled)
audit_results  (msg_id, rule_id, verdict, hmac, audited_at, auditor_device)
```

- 审计在**客户端**执行：block 级消息不进发送管道，flag 级标记随消息上云
- 审计结果携带**客户端密钥 HMAC**，服务器验签不验内容——无内容也能执行策略
- 关键策略示例：**audit 未通过的消息禁止路由进 AI 账号会话**
- 试点规则：关键词 + 正则 + 身份证/手机号模式；模型分类器留接口后补
