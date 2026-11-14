# 06 — GitHub 授权（已完成 2026-11-14）

- 账户：**juice094**
- 方式：gh CLI 设备授权流（方案 A），无令牌明文经过聊天
- Scopes：`gist, read:org, repo, workflow`
- 凭据位置：`/root/.config/gh/hosts.yml`（600，root/属主可读）
- git 凭据助手：`!/usr/bin/gh auth git-credential`

## 使用规范

- 本机 `git push` 自动携带 juice094 身份
- **每次实际推送前须说明目标仓库与内容**（ outward 动作保留用户否决点）
- 新建仓库前与用户确认名称与可见性（public/private）
