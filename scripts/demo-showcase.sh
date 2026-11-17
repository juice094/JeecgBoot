#!/usr/bin/env bash
# =============================================================================
# 演示日一键脚本：环境自检 + 登录 + 种子体检 + 守卫自检 + 打开演示页面
# 设计文档：projects/agent-collab-pilot/docs/25-demo-pack-design.md §四
# 依赖：bash + curl + python（Git Bash 环境自带）；账号默认 admin/123456（试点验证码已关），
#       可用 TEAM_DEMO_USER / TEAM_DEMO_PASS 覆盖。
# 前置：后端 scripts/start-team-backend.sh、前端 scripts/start-team-web.sh 已启动；
#       演示种子 V3.9.6_7 已由宿审定并手动执行（未执行时本脚本在种子体检阶段 FAIL 退出=2）。
# 用法：
#   bash scripts/demo-showcase.sh            # = --all（自检+登录+体检+守卫，不打开页面）
#   bash scripts/demo-showcase.sh --check    # 仅环境自检
#   bash scripts/demo-showcase.sh --guard    # 自检+登录+体检+守卫自检
#   bash scripts/demo-showcase.sh --open     # 自检+登录+体检通过后打开演示路线页面
# 退出码：0 全绿 / 1 环境、登录或守卫 FAIL / 2 种子数据缺失
# 注意：--guard 只读式触发——请求会被服务端守卫拒绝、不改数据；若守卫回归导致请求
#       意外成功，脚本以 1 退出并提示重跑种子脚本恢复演示数据。
# =============================================================================
set -uo pipefail

# 参数：首参为端口数字时视为端口，否则视为模式；也支持 `demo-showcase.sh 8081 --guard`
PORT="${TEAM_BACKEND_PORT:-8081}"
MODE="--all"
if [ $# -ge 1 ]; then
  case "$1" in
    --*) MODE="$1" ;;
    *) PORT="$1"; MODE="${2:---all}" ;;
  esac
fi
BASE="http://127.0.0.1:${PORT}/jeecg-boot"
WEB="${TEAM_WEB_URL:-http://127.0.0.1:${TEAM_WEB_PORT:-3200}}"
DEMO_USER="${TEAM_DEMO_USER:-admin}"
DEMO_PASS="${TEAM_DEMO_PASS:-123456}"

say()  { echo "$*"; }
ok()   { echo "[OK] $*"; }
fail() { echo "[FAIL] $*"; }
warn() { echo "[WARN] $*"; }

# ---- 阶段 0：环境自检（口径同 smoke-team-stack.sh） ----
check_env() {
  say "== 阶段 0：环境自检 =="
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/doc.html")" || code="000"
  if [ "$code" = "000" ]; then
    fail "doc.html 不可达：后端未启动（bash scripts/start-team-backend.sh）"
    return 1
  fi
  ok "后端 doc.html HTTP $code"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$WEB/")" || code="000"
  if [ "$code" = "000" ]; then
    warn "前端 $WEB 不可达：演示页打不开（bash scripts/start-team-web.sh）"
  else
    ok "前端 $WEB HTTP $code"
  fi
  return 0
}

# ---- 阶段 1：登录取 token ----
login() {
  say "== 阶段 1：登录（$DEMO_USER） =="
  if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
    fail "未找到 python，无法解析登录响应（Git Bash 应自带 python）"
    return 1
  fi
  local PY=python; command -v python >/dev/null 2>&1 || PY=python3
  local body
  body="$(curl -s --max-time 10 -X POST "$BASE/sys/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$DEMO_USER\",\"password\":\"$DEMO_PASS\"}")"
  TOKEN="$(echo "$body" | "$PY" -c "import sys,json;d=json.load(sys.stdin);t=(d.get('result') or {}).get('token');print(t or '')" 2>/dev/null || true)"
  if [ -z "$TOKEN" ]; then
    fail "登录失败：响应未含 result.token（账号/密码错误或验证码未关）"
    return 1
  fi
  ok "登录成功，token 已获取"
  return 0
}

# ---- 阶段 2/3：种子体检 + 守卫自检（python urllib，无第三方依赖） ----
run_checks() {
  local with_guard="$1"
  say "== 阶段 2：种子体检 =="
  python - <<PYEOF
import json, sys, urllib.request

BASE = "$BASE"
TOKEN = "$TOKEN"
GUARD = ${with_guard^}
failures = 0

def get(path):
    req = urllib.request.Request(BASE + path, headers={"X-Access-Token": TOKEN})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))

def put(path):
    req = urllib.request.Request(BASE + path, method="PUT", headers={"X-Access-Token": TOKEN})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))

def post(path):
    req = urllib.request.Request(BASE + path, method="POST", headers={"X-Access-Token": TOKEN})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))

def check(name, cond, detail=""):
    global failures
    tag = "OK" if cond else "FAIL"
    if not cond:
        failures += 1
    print(f"[{tag}] {name}" + (f" —— {detail}" if detail and not cond else ""))

def counts(body):
    r = (body or {}).get("result") or {}
    return r.get("todo") or [], r.get("doing") or [], r.get("done") or []

try:
    todo, doing, done = counts(get("/team/task/board"))
    check("任务看板三列分布", len(todo) >= 3 and len(doing) >= 3 and len(done) >= 3,
          f"todo={len(todo)} doing={len(doing)} done={len(done)}，种子未执行？")

    due = (get("/team/task/dueSoon?days=7").get("result")) or []
    check("临期任务 >= 2", len(due) >= 2, f"dueSoon={len(due)}")

    todo_page = (get("/team/approval/todo?pageNo=1&pageSize=10").get("result")) or {}
    check("admin 待审批 >= 1", (todo_page.get("total") or 0) >= 1, f"total={todo_page.get('total')}")

    bugs = (get("/team/bug/list?pageNo=1&pageSize=10").get("result")) or {}
    check("Bug 列表 >= 4（四态）", (bugs.get("total") or 0) >= 4, f"total={bugs.get('total')}")

    audit = (get("/team/audit/list?pageNo=1&pageSize=10").get("result")) or {}
    check("审计日志 >= 10", (audit.get("total") or 0) >= 10, f"total={audit.get('total')}")

    s = (get("/team/dashboard/summary").get("result")) or {}
    check("仪表盘三计数 > 0",
          (s.get("pendingApprovalCount") or 0) > 0
          and (s.get("myUnfinishedTaskCount") or 0) > 0
          and (s.get("openBugCount") or 0) > 0,
          f"summary={s}")

    ann = (get("/sys/annountCement/listByUser?pageSize=50").get("result")) or {}
    annt_list = ann.get("anntMsgList") or []
    check("公告 >= 1", len(annt_list) >= 1, f"anntMsgList={len(annt_list)}")

    if GUARD:
        print("== 阶段 3：守卫自检（请求应被服务端拒绝，不改数据） ==")
        g1 = put("/team/bug/status?id=demo-bug-004&status=open")
        ok1 = (g1.get("success") is False)
        check("closed 终态拒绝重开", ok1, f"意外放行：{g1}")
        g2 = post("/team/approval/approve?id=demo-appr-005")
        msg = g2.get("message") or ""
        ok2 = (g2.get("success") is False) and ("越权" in msg)
        check("非审批人越权被拒绝（message 含「越权」）", ok2, f"意外放行或文案漂移：{g2}")
except Exception as e:
    print(f"[FAIL] 请求异常：{e}")
    failures += 1

sys.exit(0 if failures == 0 else (2 if not GUARD else 1))
PYEOF
}

# ---- 阶段 4：打开演示路线页面 ----
open_pages() {
  say "== 阶段 4：打开演示路线 =="
  local pages=("/dashboard" "/tools/taskboard" "/tools/approval" "/tools/bug" "$BASE/doc.html")
  local p
  for p in "${pages[@]}"; do
    case "$(uname -s 2>/dev/null || echo Windows)" in
      MINGW*|MSYS*|CYGWIN*|Windows) cmd //c start "" "$WEB$p" 2>/dev/null || cmd //c start "" "$p" ;;
      Darwin) open "$p" ;;
      *) xdg-open "$p" 2>/dev/null || echo "  请手动打开: $p" ;;
    esac
    say "  已请求打开: $p"
  done
  # doc.html 是后端地址，单独处理（上面循环里对后端地址已直接 start）
  echo "  doc.html（Knife4j 在线调试，审计追溯演示用）: $BASE/doc.html"
}

# ---- 主流程 ----
check_env || exit 1
case "$MODE" in
  --check) say "（--check：环境自检完成，后续阶段跳过）"; exit 0 ;;
esac
login || exit 1
case "$MODE" in
  --open)
    if run_checks false; then open_pages; else exit $?; fi
    ;;
  --guard)
    run_checks true || exit $?
    ;;
  *)
    run_checks false || exit $?
    say "全绿。演示路线见 docs/25 §三；加 --guard 自检守卫，加 --open 打开页面。"
    ;;
esac
