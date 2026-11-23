#!/usr/bin/env bash
# 权限确权治理 E2E 冒烟脚本
# 用法：
#   bash scripts/smoke-permission-grant.sh                 # 默认 8081 / ceshi / 邮件模板列表权限
#   BASE_URL=http://127.0.0.1:8080/jeecg-boot bash scripts/smoke-permission-grant.sh
#   TARGET_USER=lanou_emp TARGET_PASS=xxx PERMISSION_ID=20261118100000000203 bash scripts/smoke-permission-grant.sh
#
# 前置：
#   1) 后端已启动且 jeecg.permission.grant.enabled=true
#   2) 迁移 _12/_13 已应用（启动日志有 Successfully applied）
#   3) TARGET_USER 能登录，且角色基线不含 PERMISSION_ID（否则基线断言无意义）
#
# ⚠ 本脚本会向 sys_permission_grant 写入真实记录（申请→审批→回收三段），属测试数据。
# 退出码：0 全过；非 0 首个失败步骤号。
set -u

BASE_URL="${BASE_URL:-http://127.0.0.1:8081/jeecg-boot}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-123456}"
TARGET_USER="${TARGET_USER:-ceshi}"
TARGET_PASS="${TARGET_PASS:-123456}"
TARGET_USER_ID="${TARGET_USER_ID:-1714471285016895490}"   # ceshi；换用户时必须同时给
PERMISSION_ID="${PERMISSION_ID:-20261118100000000203}"   # team:mail:template:list
PROTECTED_URL="${PROTECTED_URL:-$BASE_URL/team/mail/system/templates?pageNo=1&pageSize=1}"
EXPECTED_DENY="${EXPECTED_DENY:-510}"                    # Jeecg 无权限返回码

step=0; fail=0
say()  { printf '%s\n' "$*"; }
ok()   { say "[PASS] $*"; }
bad()  { say "[FAIL] step$step $*"; exit "$step"; }
json() { python -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

login() {
  curl -s -X POST "$BASE_URL/sys/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | json "d['result']['token']"
}

step=1
TOKEN=$(login "$ADMIN_USER" "$ADMIN_PASS")
[ -n "$TOKEN" ] || bad "admin 登录失败"
USER_TOKEN=$(login "$TARGET_USER" "$TARGET_PASS")
[ -n "$USER_TOKEN" ] || bad "目标用户 $TARGET_USER 登录失败"
ok "登录（admin + $TARGET_USER）"

step=2
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$PROTECTED_URL" -H "X-Access-Token: $USER_TOKEN")
BODY=$(curl -s "$PROTECTED_URL" -H "X-Access-Token: $USER_TOKEN")
say "$BODY" | grep -q "\"code\":$EXPECTED_DENY" || bad "基线期望 $EXPECTED_DENY（无权限），实际: $(say "$BODY" | head -c 120)"
ok "基线拒绝（$EXPECTED_DENY）"

step=3
GID=$(curl -s -X POST "$BASE_URL/sys/permissionGrant/apply" \
  -H "X-Access-Token: $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"USER\",\"subjectId\":\"$TARGET_USER_ID\",\"permissionId\":\"$PERMISSION_ID\",\"scopeType\":\"SELF\",\"approver\":\"$ADMIN_USER\",\"applyReason\":\"冒烟脚本自动验证\",\"validUntil\":\"2099-12-31 23:59:59\"}" \
  | json "d['result']['id']")
[ -n "$GID" ] || bad "apply 未返回授权单 ID"
ok "申请 grant=$GID"

step=4
curl -s -X POST "$BASE_URL/sys/permissionGrant/approve?id=$GID" \
  -H "X-Access-Token: $TOKEN" --data-urlencode "comment=冒烟通过" \
  | grep -q '"success":true' || bad "approve 失败"
ok "审批通过"

step=5
BODY=$(curl -s "$PROTECTED_URL" -H "X-Access-Token: $USER_TOKEN")
say "$BODY" | grep -q '"success":true' || bad "授权后期望 200，实际: $(say "$BODY" | head -c 120)"
ok "同会话立即生效（200）"

step=6
curl -s -X POST "$BASE_URL/sys/permissionGrant/revoke?id=$GID" \
  -H "X-Access-Token: $TOKEN" --data-urlencode "comment=冒烟回收" \
  | grep -q '"success":true' || bad "revoke 失败"
BODY=$(curl -s "$PROTECTED_URL" -H "X-Access-Token: $USER_TOKEN")
say "$BODY" | grep -q "\"code\":$EXPECTED_DENY" || bad "回收后期望回到 $EXPECTED_DENY，实际: $(say "$BODY" | head -c 120)"
ok "回收同会话立即失效（$EXPECTED_DENY）"

say ""
say "冒烟全过。测试数据：grant $GID（revoked），如需清理请走 SQL。"
