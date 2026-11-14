# 05 — 团队组网（Tailscale）

状态：**v1.104.7 已安装，待浏览器授权登录**（今日工作项）

## 关键约束（决定选型）

**服务器安全组由模型服务商持有，无法开放任何公网入站端口。**

- 自建 WireGuard（需入站 UDP 51820）→ **不可行**
- Tailscale 打洞 / DERP 中继，零入站端口 → **唯一可行解**
- 2026-11-14 早些时候的 WireGuard 星型方案已废弃并替换本文档

## 现状（2026-11-13 已部署）

- 官方静态包 v1.104.7：`/usr/sbin/tailscaled` + `/usr/bin/tailscale`，systemd 托管、自启已开
- 内核调优已写入 `/etc/sysctl.d/99-tailscale-tune.conf`（ip_forward、rmem/wmem、BBR）
- **未登录**。登录：`tailscale up` → 输出授权链接 → 浏览器打开授权 → 本机获得 `100.x` tailnet IP
- Clash（mihomo v1.19.21）已装：7890 mixed 出口 / 9090 面板（凭据见内部运维文档，未入库）

## 今日组网步骤

1. 服务器执行 `tailscale up`，输出授权链接（有效期有限，过期重跑）
2. 你的浏览器打开链接完成授权
3. `tailscale status` 确认本机获得 100.x IP
4. 团队成员设备装 Tailscale 客户端，登录同一 tailnet（或受邀加入）
5. 验收：成员 `ping <100.x>`，访问 jeecg 前端 `http://<100.x>/` 与 Nacos 控制台 `http://<100.x>:18080/next/`

## 端口矩阵（tailnet 内可达，与公网安全组无关）

| 端口 | 服务 |
|---|---|
| 80 | jeecg 前端 (Nginx) |
| 8080 | jeecg-boot API |
| 13306 | MySQL（原生） |
| 8066 | Mycat2 统一入口 |
| 8848 / 9848 | Nacos 3.2（HTTP / gRPC） |
| 18080 | Nacos 控制台 `/next/` |
| 7890 | Clash mixed（隔离浏览器出口预留） |
| 9090 | Clash 面板 |

## 与设备准入的绑定

- Tailscale 节点公钥（本质是 WireGuard 密钥）入 `devices` 表——schema 的 `wg_pubkey` 字段语义不变
- tailnet ACL 按用户/节点做网络层粗准入；应用层 WSS 握手再做细校验
- 可选后续：`tailscale up --advertise-routes=172.18.0.0/16` 把 docker 网段广播进 tailnet，容器 IP 直接可达
- 账号池容器需要独立出口时：`--proxy-server=http://<宿主100.x>:7890` 指向 Clash
