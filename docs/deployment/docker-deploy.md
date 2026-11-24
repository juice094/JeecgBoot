# Docker 单体全栈部署

> 2026-11-24 起随 PR #27 后补齐：MinIO / Kafka / team-web 纳入编排，端口对齐 8081。
> 适用 `docker-compose.yml`（单体）；微服务线见 `docker-compose-cloud.yml`（ dormant，未随本次补全验证）。

## 一键启动

```bash
# Windows 双击 start-docker-compose.bat；Linux/Mac：
./start-docker-compose.sh
```

脚本做两件事：① `mvn clean install -Pdocker` 构建后端 jar（激活 docker Maven profile，
过滤出 `application-docker.yml`）；② `docker compose up -d --build` 全栈构建启动。

## 服务清单

| 服务 | 镜像/构建 | 宿主端口 | 说明 |
|---|---|---|---|
| jeecg-boot-mysql | build `./jeecg-boot/db` | 13306→3306 | MySQL 8.0；首启自动导入 jeecg-boot 库 + nacos/xxl-job 表 + **db_wordsync 三表**（`db_wordsync-init.sql`） |
| jeecg-boot-redis | jeecgdocker/redis:5.0 | 内部 | Redis 会话/缓存 |
| jeecg-boot-minio | minio/minio | 9002→9000, 9003→9001 | **word-sync 强依赖**：StorageService 启动即建桶，healthcheck 不通过 system 不起 |
| jeecg-boot-kafka | apache/kafka:3.8.1 | 内部 | word-sync 事件总线（KRaft 单节点）；缺省时应用可启动但 consumer 刷重连日志 |
| jeecg-boot-system | build `./jeecg-boot/.../jeecg-system-start` | 8081→8081 | 后端单体（healthcheck 依赖齐后起） |
| jeecg-team-web | build `./jeecg-team-web` | 3200→80 | **现行默认前端**；nginx 反代 `/jeecg-boot/`（含 SSE/WebSocket 升级头） |
| jeecg-boot-pgvector | jeecgdocker/pgvector | 内部 | AI 向量库（可选消费） |

旧前端 `jeecg-vue3` 服务已下线（待迁移资产，git 历史可找回）。

## 访问入口

- 前端 http://localhost:3200 （默认账号 admin / 123456）
- 后端 http://localhost:8081/jeecg-boot ；接口文档 /doc.html
- MinIO 控制台 http://localhost:9003 （minioadmin/minioadmin，bucket=wordsync 由应用自动创建）

## 配置口径

- **数据库**：`application-docker.yml` master=jeecg-boot 库、wordsync=db_wordsync 库，均走 `jeecg-boot-mysql:3306`，root/root。
- **对象存储**：`minio.endpoint=http://jeecg-boot-minio:9000`（容器网络内 9000；宿主浏览器侧才用 9002）。
- **OnlyOffice**：`onlyoffice.*` 默认指向 `http://127.0.0.1:8081/...`，需按实际部署改；
  容器化 OnlyOffice（onlyoffice/documentserver）未纳入编排，属可选增强。
- **speech 转写链路**：`spring.ai.openai.*` 指向 127.0.0.1:11434/8000（Ollama/faster-whisper），
  容器内不可达——转写/纪要功能在容器部署下需另行提供这两个服务或改配，不影响应用启动。

## 排障

- **system 反复重启**：看 `docker logs jeecg-boot-system`。MinIO 未 healthy 时 StorageService 启动即抛
  "MinIO 初始化失败"——确认 minio 容器 `mc ready local` 通过。
- **team-web 打开白屏/404**：nginx 已配 SPA 回退；确认访问的是 3200 端口而非旧 80。
- **改了 flyway 脚本起不来**：flyway checksum 严格，已应用的脚本一字不能改（经验 144/153），取新号开新脚本。
- **镜像拉取失败**：宿主 docker 配置了加速器；`minio/minio`、`apache/kafka` 走 docker hub，网络受限时先配镜像加速。
