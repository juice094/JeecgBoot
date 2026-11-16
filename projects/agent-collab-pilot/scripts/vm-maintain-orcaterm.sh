#!/usr/bin/env bash
# vm-maintain-orcaterm.sh — 云 VM 维护一体脚本（OrcaTerm 粘贴执行）
# A. claw gateway 健康检查(异常才重启)  B. 放行 rog-x 运维密钥(SSH)  C. 接口文档自动更新  D. 日志轮转
set -uo pipefail

echo '=== A. claw gateway 健康检查（探针通过则跳过重启，避免消息黑洞） ==='
if systemctl --user is-active --quiet openclaw-gateway && curl -sf -o /dev/null http://127.0.0.1:18789/; then
  echo 'gateway active + http探针200 → 跳过重启'
else
  echo 'gateway 异常 → 重启恢复'
  systemctl --user restart openclaw-gateway 2>&1
  sleep 8
  systemctl --user is-active openclaw-gateway
fi
LOG=$(ls -t /tmp/openclaw/*.log /root/.openclaw/logs/*.log 2>/dev/null | head -1)
echo "log=$LOG"
tail -n 120 "$LOG" 2>/dev/null | grep -iE 'im|recv|inbound|error|pair|bind' | tail -20

echo '=== B. 放行 rog-x 运维密钥 ==='
grep -q teamops-win11 /root/.ssh/authorized_keys 2>/dev/null \
  || echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIIflECtusmtuUhDLuOCcnb0LSnvJBEghzxTszLlMSk9Z teamops-win11-rogx' >> /root/.ssh/authorized_keys
grep -c teamops-win11 /root/.ssh/authorized_keys

echo '=== C. 接口文档自动更新（每日 05:30 检查 main，有更新则重建+重启） ==='
cat > /etc/systemd/system/jeecg-doc-update.service <<'UNIT'
[Unit]
Description=Rebuild jeecg-doc when JeecgBoot main updated

[Service]
Type=oneshot
ExecStart=/bin/bash -c 'cd /root/jeecg-doc/JeecgBoot && git fetch origin main && if [ "$(git rev-parse origin/main)" != "$(git rev-parse main 2>/dev/null || echo none)" ]; then git reset --hard origin/main && cd jeecg-boot && mvn -q clean package -DskipTests -pl jeecg-module-system/jeecg-system-start -am && systemctl restart jeecg-doc; fi'
UNIT
cat > /etc/systemd/system/jeecg-doc-update.timer <<'UNIT'
[Unit]
Description=Daily jeecg-doc update check

[Timer]
OnCalendar=*-*-* 05:30:00
Persistent=true
Unit=jeecg-doc-update.service

[Install]
WantedBy=timers.target
UNIT
systemctl daemon-reload
systemctl enable --now jeecg-doc-update.timer
systemctl list-timers jeecg-doc-update.timer --no-pager | head -4

echo '=== D. 日志轮转 ==='
cat > /etc/logrotate.d/jeecg-doc <<'LR'
/root/jeecg-doc/*.log {
  daily
  rotate 7
  compress
  copytruncate
  maxsize 100M
}
LR
ls -la /etc/logrotate.d/jeecg-doc

echo '=== ALL_DONE（把完整输出贴回 rog-x 侧） ==='
