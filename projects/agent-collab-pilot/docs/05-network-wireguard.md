# 05 — 团队组网（WireGuard）

状态：**runbook 就绪，待执行**（今日工作项）

## 拓扑

星型：本机（云服务器）为 hub，`10.10.0.1/24`；成员设备为 peer，一设备一 IP。

选型理由：云服务器有公网 IP；WireGuard 内核级性能、配置极简；Tailscale 协调服务器在海外、Headscale 多一个运维组件，试点都不取。

## 服务器侧

```bash
sudo apt update && sudo apt install -y wireguard
umask 077
wg genkey | sudo tee /etc/wireguard/server.key | wg pubkey | sudo tee /etc/wireguard/server.pub
```

`/etc/wireguard/wg0.conf`：

```ini
[Interface]
Address    = 10.10.0.1/24
ListenPort = 51820
PrivateKey = <server.key 内容>

[Peer]
PublicKey  = <成员A公钥>
AllowedIPs = 10.10.0.2/32

[Peer]
PublicKey  = <成员B公钥>
AllowedIPs = 10.10.0.3/32
```

```bash
sudo systemctl enable --now wg-quick@wg0
sudo wg show
```

**必做：云控制台安全组放行 UDP 51820**（不是系统防火墙，是云厂商网络层，最易踩的坑）。

## 成员侧

```bash
umask 077
wg genkey | tee peer.key | wg pubkey > peer.pub   # pub 交管理员
```

`peer.conf`：

```ini
[Interface]
PrivateKey = <peer.key>
Address    = 10.10.0.2/32

[Peer]
PublicKey  = <server.pub>
Endpoint   = <服务器公网IP>:51820
AllowedIPs = 10.10.0.0/24
PersistentKeepalive = 25
```

手机用 WireGuard 官方 App 生成密钥、扫码或手动录入。

## 验收

成员 `ping 10.10.0.1` 通，且能访问绑在 `10.10.0.1` 上的内网服务（如 noVNC `http://10.10.0.1:3000`）。

## 团队管理版：wg-easy

成员 >3–5 人或需频繁增删时：

```yaml
services:
  wg-easy:
    image: ghcr.io/wg-easy/wg-easy:14
    environment:
      WG_HOST: <服务器公网IP>
      PASSWORD: '<管理面板强密码>'
      WG_DEFAULT_ADDRESS: 10.10.0.x
      WG_PERSISTENT_KEEPALIVE: "25"
    ports:
      - "51820:51820/udp"
      - "127.0.0.1:51821:51821"    # 面板只绑本地, SSH 隧道访问
    cap_add: [NET_ADMIN, SYS_MODULE]
    volumes:
      - ./wg-data:/etc/wireguard
```

与手动 wg-quick **二选一**，不要抢 51820。

## 与设备准入的绑定

- `10.10.0.x` 分配记入 `devices.assigned_ip`，`wg_pubkey` 入 `devices` 表
- 网络层 `AllowedIPs` 粗准入；应用层 WSS 握手细校验
