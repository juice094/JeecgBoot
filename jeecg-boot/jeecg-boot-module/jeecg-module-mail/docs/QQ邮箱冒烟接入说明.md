# QQ 邮箱冒烟接入说明（本地，不含明文凭证）

> 执笔：mail-dev，2026-11-18
> 用途：注册闸门开启后，用 QQ 邮箱跑通 §3 案 B 终验与 §4/§5 绑定同步。
> 安全口径：**凭证不进仓库、不写配置文件**，走环境变量与请求体；落库由 AesMailPasswordCodec 加密。

## 1. 为什么不用 config/application.yml

已核实：`jeecg-system-start/config/` 只含代码生成器 ftl 模板，`application.yml` 仅有 `spring.config.import: optional:classpath:config/application-liteflow.yml`。该目录不是 Spring Boot 外部配置目录，写入的 mail 配置不会被加载。

因此采用两条零落盘通道：

| 配置项 | 通道 | 说明 |
|---|---|---|
| jeecg.mail.crypto.key | 环境变量 `MAIL_CRYPTO_KEY` | AesMailPasswordCodec 构造器已有 fallback：`@Value("${jeecg.mail.crypto.key:${MAIL_CRYPTO_KEY:}}")` |
| QQ 授权码 | 请求体字段 | 建账号 / 绑定接口的 password 入参，落库前加密 |
| jeecg.mail.sync.enabled | 环境变量或启动参数 | 仅 §6 调度联动验证时临时开启 |

## 2. 启动前设置（git-bash）

    export MAIL_CRYPTO_KEY='w2fIbQb7dvKJd1Mo3+3AqUHHeU/b9nFMsywsvN1RaLA='
    # §6 验证时再加：export JEECG_MAIL_SYNC_ENABLED=true

> 该密钥为本次新生成（Base64 32 字节）。若后续库中已有用旧密钥加密的数据，须换回旧密钥，否则解密失败。当前库无 mail 数据，可用此密钥。

> 密钥泄露即等于密码泄露，不要提交、不要贴进 issue、不要留在 shell 历史（可用 `unset MAIL_CRYPTO_KEY` 清理）。

## 3. QQ 邮箱连接参数（已实测网络可达）

| 项 | 值 |
|---|---|
| 邮箱地址 | 2241470466@qq.com |
| IMAP host / port / SSL | imap.qq.com / 993 / true |
| SMTP host / port / SSL | smtp.qq.com / 465 / true |
| 用户名 | 2241470466@qq.com |
| 密码 | 16 位授权码（运行时由环境变量 `QQ_AUTH` 注入，不落文档） |

实测：`imap.qq.com:993 OK`、`smtp.qq.com:465 OK`（TCP 可达）。

## 4. 授权码注入

    export QQ_AUTH='<16位授权码>'

冒烟时 curl 用 `"password":"$QQ_AUTH"` 引用，文档与脚本里不出现明文。

## 5. 冒烟 §2.1 建系统发信账号（curl 模板）

    curl -s -X POST "$BASE/system/accounts" \
      -H "X-Access-Token: $TOKEN" -H "Content-Type: application/json" \
      -d "{
        \"host\":\"smtp.qq.com\",
        \"port\":465,
        \"username\":\"2241470466@qq.com\",
        \"password\":\"$QQ_AUTH\",
        \"sslEnable\":true,
        \"starttlsEnable\":false,
        \"fromAddress\":\"2241470466@qq.com\",
        \"fromName\":\"OA 系统通知（冒烟）\"
      }"

断言：`success=true`；随后查库 `select password from system_mail_account` 应为密文，不等于 `$QQ_AUTH`。

## 6. 冒烟 §2.5 测连

    curl -s -X POST "$BASE/system/accounts/$ACC_ID/test" -H "X-Access-Token: $TOKEN"

预期：`result.smtpOk=true`、`latencyMs>0`。QQ 授权码错误时 `errorCode=smtp_auth_failed`。

## 7. 冒烟 §3 案 B 发送闭环

模板 `SMOKE_TPL` 的 `to` 填 `2241470466@qq.com`（自发自收，最容易验证到达）。发送后轮询 `/system/logs/{logId}` 到 `sendStatus=2`，再登录 QQ 邮箱网页确认收到。

注意：QQ 对自发自收和高频发送有限流。冒烟期间避免连续重发同模板；若遇 `smtp_timeout` 或临时拒绝，等 5-10 分钟再试，不要误判为代码缺陷。

## 8. 冒烟 §4.1 绑定员工邮箱

    curl -s -X POST "$BASE/accounts" \
      -H "X-Access-Token: $TOKEN" -H "Content-Type: application/json" \
      -d "{
        \"email\":\"2241470466@qq.com\",
        \"imap\":{\"host\":\"imap.qq.com\",\"port\":993,\"ssl\":true,\"username\":\"2241470466@qq.com\",\"password\":\"$QQ_AUTH\"},
        \"smtp\":{\"host\":\"smtp.qq.com\",\"port\":465,\"ssl\":true,\"username\":\"2241470466@qq.com\",\"password\":\"$QQ_AUTH\"}
      }"

断言：双测连通过才落库；库中 imap_password / smtp_password 均为密文；testStatus=1。

## 9. 冒烟 §4.2 负路径（IMAP 故意错密码）

把 imap.password 改为 `wrong-auth`，其余不变。预期 `success=false`、`message` 含 `IMAP 连接失败(imap_auth_failed)`、`result.imapOk=false`，且**库中无新记录**。

## 10. 冒烟 §5 同步与列表

1. `POST /accounts/{id}/sync` 触发增量同步。
2. `GET /accounts/{id}/folders` 看 QQ 的文件夹归一：INBOX→Inbox、已发送→Sent（QQ 中文名依赖 MailFolderMapping，若未识别属预期，走 §5.3 映射接口）。
3. `GET /messages?accountId=&canonicalFolder=Inbox` 确认列表有数据、`fromAddressHash` 为 8 位、列表不触发 IMAP FETCH。
4. 再次 sync，确认唯一键去重、lastUid 推进。

## 11. QQ 特有风险

| 风险 | 表现 | 处置 |
|---|---|---|
| 高频失败限流 | 连续测连失败后临时锁定 10-15 分钟 | 负路径用例集中跑，失败后暂停再试 |
| 文件夹中文名 | 已发送/草稿箱等中文名可能未被 MailFolderMapping 识别 | 属预期，走 §5.3 映射；同时验证“未识别不静默跳过” |
| 自发自收延迟 | 到达可能延迟数秒 | 轮询间隔 2s、上限 60s 再判失败 |
| 授权码重置 | 改 QQ 密码会使授权码失效 | 冒烟期间不改密码；失效则重新生成并更新 `QQ_AUTH` |

## 12. 收尾

冒烟结束执行：

    unset MAIL_CRYPTO_KEY QQ_AUTH

并确认 `git status` 中无任何含凭证的文件。
