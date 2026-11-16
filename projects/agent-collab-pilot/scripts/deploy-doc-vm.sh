#!/usr/bin/env bash
# deploy-doc-vm.sh — 云 VM（VM-14-0-ubuntu）常驻 JeecgBoot 接口文档实例
#
# 用途：给团队一个固定的 Knife4j 文档入口（tailnet 内访问），不用于业务运行。
# 幂等：可重复执行，已就绪的步骤自动跳过。
# 前提：root 执行；本机已有 MySQL(13306)/Redis(6379)（jeecg 底座 docker 栈）；JDK 17+。
#
# 访问：http://100.78.65.37:8080/jeecg-boot/doc.html （仅 tailnet；basic auth 口令见
#       /root/jeecg-doc/jeecg-doc.env，KIMI 可见，勿外发）
set -euo pipefail

BASE=/root/jeecg-doc
REPO=$BASE/JeecgBoot
JAR=$REPO/jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-*.jar
ENV_FILE=$BASE/jeecg-doc.env
UNIT=/etc/systemd/system/jeecg-doc.service
DB_HOST=127.0.0.1
DB_PORT=13306

say() { printf '[deploy-doc] %s\n' "$*"; }
die() { printf '[deploy-doc][ERROR] %s\n' "$*" >&2; exit 1; }

# --- 0. JDK 17+ ---
java_ver=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1 || true)
[ "${java_ver:-0}" -ge 17 ] || die "需要 JDK 17+，当前: $(java -version 2>&1 | head -1)"
say "JDK OK: $(java -version 2>&1 | head -1)"

# --- 1. 端口 8080 须空闲（本机 nacos 用 8848/9848/18080，8080 应空闲） ---
if ss -tlnp | grep -q ':8080 '; then
  die "8080 已被占用：$(ss -tlnp | grep ':8080 ' | head -1)"
fi
say "8080 空闲"

# --- 2. jeecg-boot 库存在与否，缺则导入 ---
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -proot -N -e "SELECT 1 FROM information_schema.tables WHERE table_schema='jeecg-boot' LIMIT 1" 2>/dev/null | grep -q 1; then
  say "jeecg-boot 库缺失，建空库（克隆后导入数据）"
  mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -proot -e "CREATE DATABASE IF NOT EXISTS \`jeecg-boot\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  NEED_IMPORT=1
else
  say "jeecg-boot 库已存在"
  NEED_IMPORT=0
fi

# --- 3. 克隆/更新 fork ---
mkdir -p "$BASE"
if [ -d "$REPO/.git" ]; then
  git -C "$REPO" fetch origin main && git -C "$REPO" reset --hard origin/main
  say "仓库已更新到 origin/main"
else
  git clone --depth 1 -b main https://github.com/juice094/JeecgBoot.git "$REPO"
  say "仓库已克隆"
fi

# --- 3b. 补导数据（若步骤 2 建了空库） ---
if [ "$NEED_IMPORT" = "1" ]; then
  mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -proot --default-character-set=utf8mb4 jeecg-boot < "$REPO/jeecg-boot/db/jeecgboot-mysql-5.7.sql"
  say "初始化 SQL 已导入"
fi

# --- 4. 构建（无 jar 或源码更新后） ---
if ls $JAR >/dev/null 2>&1 && [ "$(find "$REPO" -name '*.java' -newer $(ls $JAR | head -1) 2>/dev/null | head -1)" = "" ]; then
  say "jar 已是最新，跳过构建"
else
  say "构建 jeecg-system-start（跳过测试）..."
  (cd "$REPO/jeecg-boot" && mvn -q clean package -DskipTests -pl jeecg-module-system/jeecg-system-start -am)
fi
JAR_FILE=$(ls $JAR | head -1)
say "jar: $JAR_FILE"

# --- 5. basic auth 口令（只生成一次） ---
if [ ! -f "$ENV_FILE" ]; then
  umask 077
  printf 'DOC_USER=doc\nDOC_PASS=%s\n' "$(head -c 12 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 16)" > "$ENV_FILE"
  say "basic auth 口令已生成: $ENV_FILE"
fi
# shellcheck disable=SC1090
. "$ENV_FILE"

# --- 6. systemd 单元 ---
cat > "$UNIT" <<EOF
[Unit]
Description=JeecgBoot API doc instance (knife4j, tailnet only)
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=$ENV_FILE
WorkingDirectory=$BASE
ExecStart=$(which java) -Xms256m -Xmx512m -jar $JAR_FILE \
  --spring.profiles.active=dev \
  --server.port=8080 \
  --spring.datasource.dynamic.datasource.master.url=jdbc:mysql://$DB_HOST:$DB_PORT/jeecg-boot?characterEncoding=UTF-8\&useUnicode=true\&useSSL=false\&tinyInt1isBit=false\&allowPublicKeyRetrieval=true\&serverTimezone=Asia/Shanghai \
  --spring.datasource.dynamic.datasource.master.username=root \
  --spring.datasource.dynamic.datasource.master.password=root \
  --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=6379 \
  --knife4j.basic.enable=true --knife4j.basic.username=\$DOC_USER --knife4j.basic.password=\$DOC_PASS \
  --jeecg.firewall.enableLoginCaptcha=false
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
say "systemd 单元已写: $UNIT"

systemctl daemon-reload
systemctl enable --now jeecg-doc
say "服务已启动，等待健康检查..."

# --- 7. 健康检查（最多 180s） ---
for i in $(seq 1 36); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -u "$DOC_USER:$DOC_PASS" "http://127.0.0.1:8080/jeecg-boot/v3/api-docs" || true)
  if [ "$code" = "200" ]; then
    say "OK → http://100.78.65.37:8080/jeecg-boot/doc.html （用户 $DOC_USER，口令见 $ENV_FILE）"
    exit 0
  fi
  sleep 5
done
die "180s 内未就绪，查日志：journalctl -u jeecg-doc -n 100"
