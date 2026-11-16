# 15 — Apifox 接入 Runbook（接口调试与文档合同）

> 2026-11-17，Kimi Code。目标：团队共用**一个** Apifox 项目，替代手工维护的接口文档。

## 一、原理

后端暴露标准 OpenAPI JSON（springdoc，Knife4j 同源）：

```
http://<主机>/jeecg-boot/v3/api-docs
```

Apifox 导入该 URL 即得全部接口定义（含 PR #14 的 `/team/task/**`）。**后端代码改了接口 → 重新导入一次**，文档即同步——这是"代码即文档"的落地方式。

## 二、建项目（宿执行一次，约 5 分钟）

1. Apifox → 新建项目「JeecgBoot 团队协作」→ 导入 → OpenAPI/Swagger → 填 `http://127.0.0.1:8081/jeecg-boot/v3/api-docs` → 确认导入
2. 环境配置（右上角环境管理）：
   - `本机调试`：前置 URL `http://127.0.0.1:8081`，每人把 host 改自己机器亦可
   - `VM文档合同`：前置 URL `http://100.78.65.37:8080`（仅对照接口是否存在，basic auth 不挡 api-docs）
3. 邀请成员：项目设置 → 成员 → qingyanjunzi / Ym3699 加入（可编辑）
4. 全局变量：登录接口跑通后把 `token` 存入环境变量，其余接口在 Header 引用 `X-Access-Token: {{token}}`

## 三、使用规矩（写进团队约定）

1. **调试一律用「本机调试」环境**（本机 8081，数据随便造）；VM 环境只用于"合同对照"
2. 登录接口：`POST /sys/login`，body `{"username":"admin","password":"123456"}`（本机验证码已关）
3. 改了接口的 PR，描述里必须写明「Apifox 是否需重新导入」（与「文档影响」段并列）
4. 不用 Apifox 时的替代：Knife4j `http://<主机>:8081/jeecg-boot/doc.html` 自带在线调试，零安装

## 四、同步节奏

| 事件 | 动作 |
|---|---|
| 后端接口 PR 合并 | 作者在 PR 标注；宿每月 1 日随巡检重导一次兜底 |
| 飞书/Apifox 自动导入（可选） | Apifox 支持定时拉取 OpenAPI URL，可配 nightly；URL 需公网可达时改用 VM 地址（tailnet 内） |

## 五、已知边界

- api-docs 不带鉴权也能读（springdoc 默认暴露）——VM 实例已用 nginx basic auth 挡住外网，本机仅限回环，**不要把 8080 的 basic auth 口令写进 Apifox 环境备注**
- `/v3/api-docs` 只含接口定义，不含 jeecg 的 MD5 请求签名逻辑——联调真实业务时优先用前端代理（签名在 defHttp 层），Apifox 适合单接口验证
