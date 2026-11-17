#!/usr/bin/env bash
# 团队后端启动脚本（单体，默认 8081，与 jeecg-team-web 的 vite 代理对齐）
# 用法：
#   bash scripts/start-team-backend.sh            # 默认 8081 / dev
#   TEAM_BACKEND_PORT=8081 bash scripts/start-team-backend.sh
#   SKIP_AM=1 bash scripts/start-team-backend.sh  # 已 install 过依赖时跳过 -am，更快
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/jeecg-boot"

PORT="${TEAM_BACKEND_PORT:-8081}"
PROFILE="${TEAM_BACKEND_PROFILE:-dev}"
AM_FLAG="-am"
if [ "${SKIP_AM:-0}" = "1" ]; then AM_FLAG=""; fi

# JDK 检查：项目要求 JDK 17；默认 PATH 里的 java 可能是 25/26，Lombok 会挂
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="java"
fi
JAVA_VER="$("$JAVA_BIN" -version 2>&1 | awk -F'"' '/version/{print $2; exit}')"
echo "[team-backend] java=$JAVA_VER  JAVA_HOME=${JAVA_HOME:-<unset>}"
case "$JAVA_VER" in
  17*) ;;
  *) echo "[team-backend][warn] 当前 JAVA_HOME 不是 JDK 17，编译可能因 Lombok 失败；建议 export JAVA_HOME=<jdk-17>" ;;
esac
echo "[team-backend] port=$PORT profile=$PROFILE"
echo "[team-backend] 健康检查：http://127.0.0.1:$PORT/jeecg-boot/doc.html"

# 首次必须带 -am 构建 jeecg-module-team 等依赖；之后可 SKIP_AM=1 加速
exec mvn spring-boot:run \
  -pl jeecg-module-system/jeecg-system-start $AM_FLAG \
  -DskipTests \
  -Dspring-boot.run.profiles="$PROFILE" \
  -Dspring-boot.run.arguments="--server.port=$PORT"
