# 本地启动脚本（团队协作工作台）

单体形态：后端 8081 + 前端 3200 + MySQL + Redis。不涉及 Nacos / MyCat2 / Gateway。

## 端口口径（重要）

- application-dev.yml 里写的是 server.port: 8080；
- jeecg-team-web/vite.config.ts 代理目标是 http://127.0.0.1:8081；
- 两者不一致，所以启动脚本强制 --server.port=8081，命令行优先级高于配置文件，不改文件。

## 三个脚本

| 脚本 | 作用 |
|---|---|
| scripts/start-team-backend.sh | 起后端（8081，dev profile），首次带 -am 构建依赖模块 |
| scripts/start-team-web.sh | 起前端（127.0.0.1:3200），缺依赖先 pnpm install |
| scripts/smoke-team-stack.sh | 冒烟自检：doc.html、team 接口可达性、前端端口 |

## 用法

终端 1（后端，首次约数分钟，之后可 SKIP_AM=1 加速）：

    bash scripts/start-team-backend.sh

终端 2（前端）：

    bash scripts/start-team-web.sh

终端 3（自检）：

    bash scripts/smoke-team-stack.sh 8081

访问：前端 http://127.0.0.1:3200 ，Knife4j http://127.0.0.1:8081/jeecg-boot/doc.html ，账号 admin / 123456。

## JDK 要求

项目要求 JDK 17。本机 PATH 里的 java 可能是 25/26，会导致 Lombok 编译失败。启动脚本会打印 java -version，若不是 17 会给出警告。

    export JAVA_HOME=/path/to/jdk-17

## 常见故障

| 现象 | 排查 |
|---|---|
| 前端请求 502/连接拒绝 | 后端实际端口不是 8081；看控制台 Tomcat started on port |
| 前端请求 404 | vite.config.ts 代理 target 被改过 |
| smoke 报 /team/** 000 | 后端未启动或端口错；401 属正常（未登录） |
| 编译报 Lombok 错误 | JAVA_HOME 不是 JDK 17 |
| Flyway 校验失败 | 新增脚本编号冲突或已执行过同名版本 |
