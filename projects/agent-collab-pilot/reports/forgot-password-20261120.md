# /login 忘记密码功能落地报告

> 日期：2026-11-20
> 执行：AI 编码助手（Kimi Code，主会话施工）
> 触发：现行前端 jeecg-team-web 的 /login 无"忘记密码"入口
> 方案：宿拍板——前端实现 MD5 签名（不动后端 signUrls）+ 后端加 dev 短信 mock

## 1. 现状与口径（排查结论）

- 后端能力本来就齐全（JeecgBoot 原生，全部匿名）：`POST /sys/sms`（smsmode=2 忘记密码模板）、`POST /sys/user/phoneVerification`、`GET /sys/user/passwordChange`。Shiro 匿名名单见 `ShiroConfig.java:114-119`。
- 两个阻塞点：
  1. `/sys/sms` 在 `jeecg.signUrls`（`application-dev.yml:300`）名单内，走 `SignAuthInterceptor` MD5 签名校验，team-web 原 http.ts 只有 token 头、无签名。
  2. dev 环境阿里云短信 key 为 `??` 占位（`application-dev.yml`），真实短信发不出去。
- 旧前端参考实现：`jeecgboot-vue3/src/views/system/loginmini/MiniForgotpad.vue` + `src/utils/encryption/signMd5Utils.js`。

## 2. 改动清单

### 后端（jeecg-boot）

| 文件 | 改动 |
|---|---|
| `jeecg-boot-base-core/.../config/JeecgBaseConfig.java` | 新增 `smsMock` 字段（Boolean，默认 false，lombok getter/setter） |
| `jeecg-system-start/.../application-dev.yml` | `jeecg.smsMock: true`（注释标明 dev only、生产严禁开启） |
| `jeecg-system-biz/.../controller/LoginController.java` | 新增私有方法 `sendSmsWithMockSupport(mobile, obj, templateEnum)`：mock=true 时 warn 日志打印验证码并返回 true，否则走 `DySmsHelper.sendSms`；4 处调用点（注册/登录/忘记密码/修改密码模板）全部替换 |

编译验证：`mvn compile -pl jeecg-module-system/jeecg-system-biz -am` 通过。

### 前端（jeecg-team-web）

| 文件 | 改动 |
|---|---|
| `src/utils/md5.ts` | 新增：RFC 1321 MD5 紧凑实现（UTF-8 安全），零新依赖 |
| `src/utils/sign.ts` | 新增：`getSign(url, params, data)` 移植自 jeecgboot-vue3 signMd5Utils，与后端 `SignUtil.getParamsSign` 逐字对齐（键升序、剔 `_t`、数值/布尔转字符串、MD5(JSON+密钥)大写） |
| `src/utils/http.ts` | 请求拦截器统一加 `X-Sign`/`X-TIMESTAMP` 头（所有请求都带，后端仅对 signUrls 名单校验，其余忽略） |
| `src/api/auth.ts` | 新增：sendForgotPasswordSms / verifyPhoneCode / resetPasswordByPhone，兼容 code=200 与 success=true 两种返回 |
| `src/views/ForgotPassword.vue` | 新增：三步流程（手机号发码 → 验证码校验 → 设新密码），视觉复用 Login.vue 令牌 |
| `src/router/index.ts` | 新增 `/forgot-password` 路由 + 守卫匿名白名单 |
| `src/views/Login.vue` | "忘记密码？"入口链接；`?reset=1` 时显示"密码已重置"提示 |

验证：`pnpm typecheck` 通过；md5 与 node:crypto 对拍 6 条测试向量（含中文、长串）全过；getSign 与后端签名规则对拍通过。

## 3. 使用与联调要点

1. **重启 8081 后端**加载 smsMock 配置与新代码（当前运行进程仍是旧构建）。
2. **目标账号必须已绑定手机号**：/sys/sms 会先按手机号查用户（`getUserByPhone`），admin 若未绑手机号会返回"该用户不存在或未绑定手机号"。可在人事管理页或直接在 sys_user.phone 补录。
3. dev 下发码后，**验证码打印在后端控制台 warn 日志**（`[SMS-MOCK] 手机号:xxx 模板:xxx 验证码:xxxxxx`），前端页面也做了提示。
4. 重置成功后跳回 /login 并提示用新密码登录。

## 4. 回滚

| 改动 | 方式 | 成本 |
|---|---|---|
| smsMock | yml 置回 false 或删除 | 一行配置 |
| 前端全部 | git revert（7 个文件均为新增或小幅修改） | 低 |
| 签名头 | 保留无副作用（后端只对名单接口校验） | — |

## 5. 已知边界（有意为之）

- **未接图形验证码**：后端有按 IP/手机号的限流（DySmsLimit）兜底；若出现"短信接口请求太多"提示（QQYUN-9005 场景），再补 /sys/randomImage + /sys/smsCheckCaptcha 流程。
- **passwordChange 是 GET + query**：上游原生口径，密码会出现在 URL/日志里，迁移期不改；若做安全加固需后端改成 POST，单独立项。
- **签名密钥硬编码在前端**：与旧前端、后端默认值一致的公开常量；生产部署应改 `jeecg.signatureSecret` 并同步前端 sign.ts。
- 短信 mock **只影响 dev profile**；application-prod.yml 等未加 smsMock 配置，默认 false。

## 6. 关联

- 在线用户重复问题修复报告：`projects/agent-collab-pilot/reports/online-user-dup-fix-20261120.md`（同日）
