# jeecg-module-word-sync

原独立 `word-sync` 工程合并进 JeecgBoot 后的单模块版本。

## 访问路径

JeecgBoot 的 context-path 为 `/jeecg-boot`，本模块所有端点统一前缀 `/word-sync`：

```text
静态页：/jeecg-boot/word-sync/index.html
REST：  /jeecg-boot/word-sync/api/documents/...
OnlyOffice 回调：/jeecg-boot/word-sync/api/onlyoffice/callback/{docId}
WebDAV：/jeecg-boot/word-sync/webdav/{docId}
WebSocket：ws://host:8081/jeecg-boot/word-sync/ws/docs?docId=...&userId=...
```

## 数据源

使用 Jeecg 的 dynamic-datasource 独立数据源 `wordsync`，数据库为 `db_wordsync`，
不迁移 word-sync 表到 Jeecg 的 `jeecg-boot` 库。

配置在 `jeecg-system-start/src/main/resources/application-dev.yml`：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        wordsync:
          url: jdbc:mysql://127.0.0.1:3306/db_wordsync...
```

Mapper 和事务 Service 使用 `@DS("wordsync")`。

## 依赖与扫描

- `jeecg-system-start` 增加 `jeecg-module-word-sync` 依赖；
- `JeecgSystemApplication` 扫描 `com.lanou.wordsync`；
- `WordSyncModuleConfig` 负责 `@MapperScan`、`@EnableKafka`、`@EnableScheduling`；
- `mybatis-plus.mapper-locations` 增加 `classpath*:mapper/*.xml`；
- `ShiroConfig` 增加 `/word-sync/**` 匿名访问（内部业务页，无独立登录）。

## 外部服务

`application-dev.yml` 中增加：

```yaml
minio:
  endpoint: http://127.0.0.1:9002
  access-key: minioadmin
  secret-key: minioadmin
  bucket: wordsync

onlyoffice:
  server-url: http://127.0.0.1
  document-base-url: http://host.docker.internal:8081/jeecg-boot/word-sync
  callback-base-url: http://host.docker.internal:8081/jeecg-boot/word-sync
```

## 前端

团队前台 `jeecg-team-web` 的 `ResourceCenter.vue` 使用 iframe：

```text
http://127.0.0.1:8081/jeecg-boot/word-sync/index.html?embed=1
```

word-sync 页面自身已改为：

```js
const API = '/jeecg-boot/word-sync';
```

## 构建

```powershell
cd E:\project\JeecgBoot\jeecg-boot
mvn -DskipTests -Pdev -pl jeecg-module-system/jeecg-system-start -am package
```

启动：

```powershell
java -jar jeecg-system-start/target/jeecg-system-start-3.9.6.jar `
  --server.port=8081 `
  --spring.profiles.active=dev `
  --jeecg.firewall.enable-login-captcha=false
```

## 已验证

- 应用在 8081 启动；
- 登录 `admin/123456`；
- word-sync 文档列表、上传、删除；
- 静态页 `/word-sync/index.html`；
- editor-config；
- WebDAV PROPFIND 207；
- Kafka 消费端可启动（audit consumer 独立 group）。
