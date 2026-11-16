# 14 — 云 VM 布局收敛（2026-11-17 盘点 + 裁决建议）

> 依据 issue #7。盘点人：Kimi Code；VM：VM-14-0-ubuntu（100.78.65.37，腾讯云 4C/8G/40G）。

## 一、VM 实际在跑什么（2026-11-17 19:02 实测）

| 类别 | 内容 |
|---|---|
| systemd 服务 | `jeecg-doc.service`（接口文档实例：nginx 8080 反代 → jar 回环 8081）、`nginx`、`docker` |
| docker 容器 | mysql(13306)、redis、nacos(8848/9848/18080)、jeecg-system-cloud-start（微服务）、jeecg-boot-gateway(9999)、mycat2、jeecgboot-vue3-nginx（原生前端展示） |
| 磁盘 | 26G/40G（68%），prune 后 13G 可用 |
| SSH | tailnet 直连正常（11-16 的 22 端口超时已恢复） |

## 二、裁决：VM 角色单一化

**VM 的唯一产品角色 = 团队共享入口机**：接口文档合同（8080）+ 原生前端展示（jeecgboot-vue3-nginx）。

1. **微服务栈（nacos/gateway/system-cloud/mycat2）降级为"研究/压测用途"**，不进产品部署图。理由：docs/07 微服务价值评估未过线，试点规模单体足够；8080 文档实例用 systemd+jar 已稳定，与微服务栈无关。
2. **部署布局唯一基准 = `docker-compose.yml`（单体）**。仓库内 `docker-compose-cloud.yml` 保留但标注"研究线，未维护"；systemd+jar 仅作 VM 文档实例特例，脚本 `deploy-doc-vm.sh` 为准。
3. 后续若重启微服务线（需技术总监拍板），先在 docs/07 补评估再动 VM。

## 三、已执行运维项（2026-11-17）

- `docker image prune -f` + `docker builder prune -f`：回收 **0B**（悬空镜像与构建缓存此前已由维护脚本清干净）。STATUS 待办 #1 就此关闭。
- SSH 通道验证：teamops 密钥可用，tailnet 稳定。

## 四、端口全景（VM 实测 + 本机，docs/13 §五 的补充）

| 端口 | 归属 | 说明 |
|---|---|---|
| 8080 | VM nginx | **接口文档合同入口**（basic auth） |
| 8081 | VM 回环 | jeecg-doc jar（仅本机回环，防绕过） |
| 13306 | VM docker | MySQL（jeecg 底座库） |
| 8848/9848/18080 | VM docker | Nacos（研究线，保留观察） |
| 9999 | VM docker | Gateway（研究线，保留观察） |
| 8066 | VM docker | MyCat2（研究线，保留观察） |
| 9090 | VM | 监控（claw 维护栈） |
| 1053/2222/7890-7893/18789/18791/52258/54235 | VM | 运维/隧道端口，非产品面，勿写入对外文档 |
| 3200/8081(本机) | 本机 | 开发主战场（docs/13 §五） |

## 五、遗留

1. VM 磁盘 68% 健康但无冗余——每月 1 日随 health-check 同节奏盘一次（`df -h` + `docker system df`）。
2. `jeecgboot-vue3-nginx` 容器与文档实例抢"前端展示"定位，若团队前端（:3200 系）需要常驻展示位，优先替换它而非新开容器。
3. 微服务栈四个容器长期占内存（4C/8G 机），若连续 30 天无研究使用，建议停机留数据（停机需宿确认）。
