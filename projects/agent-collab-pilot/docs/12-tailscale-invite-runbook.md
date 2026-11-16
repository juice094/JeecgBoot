# 12 — Tailscale 成员邀请与云 VM 文档访问 Runbook

> 目标：成员（Ym3699 / qingyanjunzi）加入 tailnet 后，能访问云 VM 上团队共用的后台接口文档
> （http://100.78.65.37:8080/jeecg-boot/doc.html）。
> 操作主体：宿（tailnet 管理员）。CLI 无法发邀请，步骤 1-2 必须在 admin 控制台完成。

## 前置条件
- [ ] 云 VM 接口文档实例已部署（issue #6，阻塞项=SSH 授权本机公钥）

## 步骤 1：邀请成员（admin 控制台，5 分钟）

1. 打开 https://login.tailscale.com/admin/users
2. 点 **Invite users** → 输入成员邮箱（或点 **Generate invite link** 把链接发微信）
3. 成员侧：安装 Tailscale 客户端（Win/Mac/Android/iOS 均有）→ 用受邀邮箱登录 → 设备出现在 Machines 列表

邀请发出后状态为 pending，成员首次登录即生效，无需你再确认。

## 步骤 2：访问控制（ACL）

**默认 ACL 是 allow-all**（tailnet 内任意用户可访问任意设备的任意端口）——成员加入后即可直接访问
100.78.65.37:8080，本步可先跳过。

当 tailnet 变大需要收紧时，admin 控制台 → **Access controls**，参考：

```json
{
  "groups": {
    "group:team": ["juice094@github", "Ym3699@github", "qingyanjunzi@github"]
  },
  "tagOwners": { "tag:vm": ["juice094@github"] },
  "acls": [
    { "action": "accept", "src": ["group:team"], "dst": ["tag:vm:8080,8848"] }
  ]
}
```

（把 VM 打上 tag:vm：Machines → 该机器 → ⋯ → Edit ACL tags）

## 步骤 3：把文档口令交给成员

部署脚本生成的 basic auth 口令在 VM 的 `/root/jeecg-doc/jeecg-doc.env`：

```bash
ssh root@100.78.65.37 cat /root/jeecg-doc/jeecg-doc.env
```

将 `DOC_USER` / `DOC_PASS` 通过私聊发给成员（不要发群里、不要进 git）。

## 步骤 4：成员验证清单

1. 设备 tailscale status 能看到 `vm-14-0-ubuntu`
2. 浏览器开 http://100.78.65.37:8080/jeecg-boot/doc.html → 弹 basic auth → 输入口令
3. 能看到「JeecgBoot 后台服务API接口文档」即全线打通

## 故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 成员 tailscale 连上但打不开 8080 | 文档实例未部署/未运行 | `ssh root@100.78.65.37 systemctl status jeecg-doc` |
| 401 Unauthorized | 口令错 | 重发 jeecg-doc.env 内容 |
| 成员设备列表里没有 | 邀请未接受 | 重发邀请链接（admin/users → pending） |
| 通但极慢 | DERP 中继（本机换网时常发） | 两端 `tailscale ping` 看是否走中继，等打洞恢复或重启 tailscaled |
