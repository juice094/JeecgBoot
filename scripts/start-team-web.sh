#!/usr/bin/env bash
# 团队前端启动脚本（jeecg-team-web，固定 3200，代理 /jeecg-boot -> 127.0.0.1:8081）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/jeecg-team-web"

if [ ! -d node_modules ]; then
  echo "[team-web] node_modules 缺失，执行 pnpm install ..."
  pnpm install
fi

echo "[team-web] http://127.0.0.1:3200  （后端需在 8081 运行）"
exec pnpm dev --host 127.0.0.1 --port 3200
