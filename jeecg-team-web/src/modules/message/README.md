# 用户与消息（聊天工作台）— qingyanjunzi

> 2026-11-17 修订（docs/13 §一，issue #9）：IM 路线改为 Mattermost 集成，自研消息线封存。

## 范围
- 聊天工作台的**入口与展示**：Mattermost 集成（嵌入/深链）、未读数与通知聚合
- 系统消息/公告收件箱（jeecg 原生 `sys_message`/`sys_notice` 能力编排）

## 对接
- HTTP 出口统一用 `@/utils/http`（baseURL `/jeecg-boot`，token 已自动带）
- 消息收发/存储/推送**不自研**，由 Mattermost 承担（M2 部署验收：Ym3699）
- ~~数据表 `conversations` / `conv_members` / `messages` / `inbox`~~ 已封存（docs/03 研究线，docs/11 裁决），**不是**本模块对接合同

## 约束
- 只改本目录（`src/modules/message/`）与路由表中 `/message/**` 段
- 跨模块需求（如需组织架构选择器）在 issue 里提，复用 admin 模块导出组件
