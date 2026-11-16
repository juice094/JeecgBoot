#!/usr/bin/env bash
# repo-poll.sh — 零 LLM 仓库轮询（juice094/JeecgBoot）
# 每轮：比对 协作者/分支head/PR 与 state/repo-watch.json；有变更则刷新 state、
# 写 state/pending-change.json（结构化 diff），并触发 repo-watch-alert cron 播报。
# 只读仓库，绝不 push。系统 crontab 驱动：*/20 8-23 * * *
set -u

REPO="juice094/JeecgBoot"
BASE="/root/.openclaw/workspace/projects/agent-collab-pilot"
STATE="$BASE/state/repo-watch.json"
LOG="$BASE/state/poll.log"
ALERT_JOB_ID="521d3f55-a789-49e4-baeb-c508ddf5b4ab"

ts()  { date '+%Y-%m-%dT%H:%M:%S+08:00'; }
log() { printf '[%s] %s\n' "$(ts)" "$1" >> "$LOG"; }

# --- 拉取（gh 已认证为 juice094）；瞬时网络故障重试 2 次，避免空值静默 QUIET 吞掉变更窗口 ---
gh_fetch() {
  local desc="$1"; shift
  local out
  for attempt in 1 2 3; do
    if out=$("$@" 2>>"$LOG"); then
      printf '%s' "$out"
      return 0
    fi
    log "WARN: $desc fetch failed (attempt $attempt)"
    sleep 3
  done
  log "ERROR: $desc fetch failed after 3 attempts"
  return 1
}

members=$(gh_fetch collaborators gh api "repos/$REPO/collaborators" --jq '[.[].login]') \
  || { log "ERROR: collaborators fetch failed"; exit 1; }
branches=$(gh_fetch branches gh api "repos/$REPO/branches?per_page=100" --jq '[.[] | {name: .name, sha: .commit.sha}]') \
  || { log "ERROR: branches fetch failed"; exit 1; }
prs=$(gh_fetch prs gh pr list --repo "$REPO" --state all --json number,title,author,state,updatedAt \
      --jq '[.[] | {number, title, author: .author.login, state, updatedAt}]') \
  || { log "ERROR: pr list failed"; exit 1; }

# 空值兜底：gh 退出 0 但输出非 JSON（限流页/空响应）时 --argjson 会炸，这里预检
case "$members" in '['*']') ;; *) log "ERROR: members non-JSON: ${members:0:80}"; exit 1;; esac
case "$branches" in '['*']') ;; *) log "ERROR: branches non-JSON: ${branches:0:80}"; exit 1;; esac
case "$prs" in '['*']') ;; *) log "ERROR: prs non-JSON: ${prs:0:80}"; exit 1;; esac

# --- 与 state 比对（jq） ---
report=$(jq -n \
  --slurpfile s "$STATE" \
  --argjson members "$members" \
  --argjson branches "$branches" \
  --argjson prs "$prs" '
  ($s[0]) as $st
  | ($st.expected_members) as $em
      | {
          repo: $st.repo,
          checked_at: "'$(ts)'",
          changes: {
            new_members:    ($members - $em),
            lost_members:   ($em - $members),
            new_branches:   [$branches[]  | .name as $n | select(($st.known_branches | has($n)) | not)],
            moved_branches: [$branches[]  | .name as $n | select(($st.known_branches | has($n)) and ($st.known_branches[$n] != .sha))],
            gone_branches:  ($st.known_branches | keys - [$branches[].name]),
            new_prs:        [$prs[]        | .number as $n | select(($st.known_prs | index($n)) == null)],
            moved_prs:      [$prs[]        | .number as $n | select(($st.known_prs | index($n)) != null)]
          }
        }
  | .has_change = (
      (.changes.new_members | length) > 0 or
      (.changes.lost_members | length) > 0 or
      (.changes.new_branches | length) > 0 or
      (.changes.moved_branches | length) > 0 or
      (.changes.gone_branches | length) > 0 or
      (.changes.new_prs | length) > 0 or
      (.changes.moved_prs | length) > 0
    )')

if ! jq -e '.has_change' <<<"$report" > /dev/null; then
  # 无变更：仅刷新 last_check
  jq --arg t "$(ts)" '.last_check = $t' "$STATE" > "$STATE.tmp" && mv "$STATE.tmp" "$STATE"
  log "QUIET"
  exit 0
fi

# --- 有变更：抓取 moved/new 分支的新提交并校验作者 ---
expected_json=$(jq -c '.expected_members' "$STATE")
unknown_authors="[]"
commits_summary="[]"
for b in $(jq -r '.changes.moved_branches[].name, .changes.new_branches[].name' <<<"$report"); do
  since=$(jq -r '.last_check // empty' "$STATE")
  # URL编码 + 号（GitHub 把裸 + 当空格→since解析失败返回200空数组，静默吞掉变更窗口）
  since_enc=${since//+/%2B}
  args=(api "repos/$REPO/commits?sha=$b&per_page=10")
  [ -n "$since" ] && args=(api "repos/$REPO/commits?sha=$b&since=$since_enc&per_page=10")
  cs=$(gh "${args[@]}" --jq '[.[] | {sha: .sha[0:7], author: (.author.login // "unknown"), message: (.commit.message | split("
")[0])}]' 2>>"$LOG") || continue
  # cs 必须是 JSON 数组；gh 200空响应/异常输出时跳过，避免污染下游 --argjson
  case "$cs" in '['*']') ;; *) log "WARN: commits non-JSON for $b: ${cs:0:80}"; continue;; esac
  unknown=$(jq -n --argjson exp "$expected_json" --argjson cs "$cs" '[ $cs[] | select(.author as $a | ($exp | index($a)) == null) ]')
  unknown_authors=$(jq -n --argjson a "$unknown_authors" --argjson b "$unknown" '$a + $b | unique')
  commits_summary=$(jq -n --argjson a "$commits_summary" --argjson cs "$cs" --arg br "$b" '[ $a[], ($cs[] | . + {branch: $br}) ]')
done

report=$(jq -n --argjson r "$report" --argjson ua "$unknown_authors" --argjson cs "$commits_summary" '
  $r | .changes.unknown_authors = $ua | .changes.new_commits = $cs')

# --- 落盘：state 刷新（known_branches/known_prs/last_change_seen） ---
jq --arg t "$(ts)" --argjson branches "$branches" --argjson prs "$prs" '
  .last_check = $t
  | .last_change_seen = $t
  | .known_branches = (reduce $branches[] as $b ({}; .[$b.name] = $b.sha))
  | .known_prs = [$prs[].number]
' "$STATE" > "$STATE.tmp" && mv "$STATE.tmp" "$STATE"

# --- 触发播报：把报告注入 alert job 的 message（agent 无需读文件/调工具，规避空 tool_result bug） ---
msgfile=$(mktemp)
cat > "$msgfile" <<MSGEOF
你是仓库告警播报 agent。仓库：juice094/JeecgBoot。以下是 repo-poll.sh 预计算的变更数据（JSON）：
$(jq -c '.' <<<"$report")

直接输出中文巡检简报，严格遵守：
- ≤15 行；首行【仓库巡检】+ checked_at
- 变更逐条一行：分支（新 head 短 sha + 作者 + 提交信息）；PR（#编号 + 标题 + 作者 + 状态）
- 成员变动单独一节（新加入/离队）
- ⚠️ 告警置顶：unknown_authors 非空 或 名单外协作者变动
- 末行：下一步建议（待合并/待评审分支）
- 禁止使用任何工具，直接回复简报文本
MSGEOF

if command -v openclaw > /dev/null 2>&1; then
  if openclaw cron edit "$ALERT_JOB_ID" --message "$(cat "$msgfile")" > /dev/null 2>>"$LOG"; then
    # 回读验证：确认 message 真含变更 JSON 再触发，杜绝空消息假告警（11-16 两次假告警根因）
    verify=$(openclaw cron list --json 2>/dev/null | jq -r --arg id "$ALERT_JOB_ID" '(.jobs // .)[] | select(.id == $id) | .payload.message // ""')
    if printf '%s' "$verify" | grep -q '"changes"'; then
      openclaw cron run "$ALERT_JOB_ID" > /dev/null 2>>"$LOG" \
        && log "ALERT triggered" \
        || log "ERROR: cron run failed"
    else
      log "ERROR: message verify failed (无 changes 标记)，skip run"
    fi
  else
    log "ERROR: cron edit failed"
  fi
  # 失败重试一次（k2d8 偶发空 tool_call 导致 400）
  sleep 50
  st=$(openclaw cron runs --id "$ALERT_JOB_ID" 2>/dev/null | jq -r '.entries[0].status // "unknown"')
  if [ "$st" != "ok" ]; then
    log "alert run status=$st, retry once"
    sleep 15
    openclaw cron run "$ALERT_JOB_ID" > /dev/null 2>>"$LOG"
  fi
else
  log "ERROR: openclaw CLI not found"
fi
rm -f "$msgfile"
