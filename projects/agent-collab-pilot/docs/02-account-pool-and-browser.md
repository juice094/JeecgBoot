# 02 — 账号池与隔离浏览器

## 核心原语

一账号一容器。人工与自动化**共用同一 profile 目录**，靠**会话锁**互斥：

- 人工经 VNC/noVNC 手动登录 webAI（登录态落盘持久化）
- 自动化经 CDP（Playwright `connect_over_cdp`）复用会话执行任务
- 任一时刻只允许一侧持有会话锁；调度器负责仲裁与通知

## 容器镜像要点

基础镜像自研（避开 KasmVNC 系——协议私有，无法接入 Guacamole/经典 VNC）：

```dockerfile
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y \
      chromium openbox x11vnc xvfb supervisor
# supervisord 拉起: Xvfb → openbox → x11vnc(:5900) → chromium
# chromium 关键启动参数:
#   --remote-debugging-port=9222
#   --user-data-dir=/data/profile    # Chrome 136+ 必须非默认目录才允许 CDP
#   --proxy-server=...               # 按账号注入独立代理(可选)
#   --no-sandbox --disable-dev-shm-usage
VOLUME /data        # 挂 ./pool/<账号ID>/ 宿主机目录, cookie 跨重启存活
EXPOSE 5900 9222
```

注意：`--user-data-dir` 指到唯一 profile 目录，人工登录与 CDP 自动化才能共享登录态；两侧同时启动会因 `SingletonLock` 冲突，所以会话锁不是可选项，是硬约束。

## 账号生命周期

```
provisioned → needs_login → healthy ⇄ in_use → (风控/过期) → needs_login
                  ↑________________人工登录_________________|
```

1. 管理员登记账号 → 生成容器 + volume + 网关连接项
2. 人工门户接管 → VNC 手动登录 → 释放
3. 巡检（CDP 打开已知 URL 判 title 是否跳登录页）→ `healthy`
4. 调度器按任务占用锁 → 执行 → 释放
5. 失效 → 标记 `needs_login` → 通知人工重登

## 出口 IP 与风控

同机容器共享服务器公网 IP，平台做 IP 维度关联风控时一损俱损。缓解：每容器注入独立代理（静态住宅 IP，`--proxy-server` 或 gluetun 边车）。**注意：这只降低账号间关联，不等于合规**——webAI ToS 普遍禁止多账号/会话共享，账号必须按可消耗资源设计，状态机含 `dead`。

## 资源估算

每 Chromium 实测 0.5–1GB RAM。本机 4C8G 起步可扛网关 + 10 左右容器；池子扩大走控制面/数据面分离横向扩。

## 备选：管控更重的方案

- **Apache Guacamole**：多租户、会话录制审计现成，代价是部署与运维面变大。试点后期若人工侧审计要求提高再引入。
