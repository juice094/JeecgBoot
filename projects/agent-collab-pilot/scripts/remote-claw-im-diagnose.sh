#!/usr/bin/env bash
# remote-claw-im-diagnose.sh — 云 VM KimiClaw "只收心跳不收消息" 诊断+初修
# 用法：腾讯云控制台 OrcaTerm 网页终端粘贴执行，输出贴回 rog-x 侧分析
set -uo pipefail

echo '=== 1. fail2ban 封禁（解除 rog-x） ==='
fail2ban-client status sshd 2>/dev/null | head -5 || echo 'no fail2ban'
fail2ban-client set sshd unbanip 100.97.76.85 2>/dev/null && echo '已解封 rog-x'

echo '=== 2. gateway 状态 ==='
systemctl --user is-active openclaw-gateway
pgrep -af 'openclaw.*gateway' | head -3
ss -tlnp | grep 18789 || echo '18789 未监听(绑127.0.0.1属正常)'

echo '=== 3. IM 接收相关日志（近 200 行过滤） ==='
LOG=$(ls -t /tmp/openclaw/*.log /root/.openclaw/logs/*.log 2>/dev/null | head -1); echo "log=$LOG"
tail -n 200 "$LOG" | grep -iE 'im|recv|inbound|message|heartbeat|pair|bind' | tail -40

echo '=== 4. 上游 WS 连通 ==='
curl -s -o /dev/null -w 'http_code=%{http_code}\n' --max-time 8 https://kimi-api-v2.msh.team/

echo '=== 5. 重启 gateway 后状态 ==='
systemctl --user restart openclaw-gateway
sleep 8
systemctl --user is-active openclaw-gateway
