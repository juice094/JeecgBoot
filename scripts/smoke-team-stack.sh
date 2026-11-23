#!/usr/bin/env bash
# 团队栈冒烟自检：后端端口 + Knife4j + team 接口可达性
# 用法：bash scripts/smoke-team-stack.sh [port]
set -uo pipefail

PORT="${1:-${TEAM_BACKEND_PORT:-8081}}"
BASE="http://127.0.0.1:${PORT}/jeecg-boot"

echo "== 后端 8081 =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/doc.html" || echo '000')"
if [ "$CODE" = "000" ]; then
  echo "[FAIL] $BASE/doc.html 不可达：后端未启动或端口不对"
  echo "       排查：后端控制台 'Tomcat started on port' 实际端口；vite.config.ts 代理 target"
else
  echo "[OK] doc.html HTTP $CODE"
fi

echo "== team 接口可达性（401 也算端口通） =="
for p in /team/task/board /team/dashboard/summary /team/approval/todo /team/audit/list; do
  C="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE$p" || echo '000')"
  case "$C" in
    200|401|403) echo "[OK] $p HTTP $C" ;;
    000) echo "[FAIL] $p 不可达" ;;
    *) echo "[WARN] $p HTTP $C" ;;
  esac
done

echo "== 前端 3200 =="
C="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:3200/ || echo '000')"
if [ "$C" = "000" ]; then echo "[SKIP] 前端未启动（bash scripts/start-team-web.sh）"; else echo "[OK] 前端 HTTP $C"; fi

echo "== 说明 =="
echo "未登录时 /team/** 返回 401 属正常；返回 000 才是端口/代理问题。"
