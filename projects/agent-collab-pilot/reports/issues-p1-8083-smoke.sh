#!/bin/bash
# /issues P1 8083 冒烟脚本（验证级：请求级留证）
set -u
BASE=http://127.0.0.1:8083/jeecg-boot
OUT=/c/Users/22414/dev/JeecgBoot/projects/agent-collab-pilot/reports/issues-p1-8083-smoke.log

TOKEN=$(curl -s -X POST $BASE/sys/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","captcha":"x7yw","checkKey":"smoke-check-key"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['result']['token'])")
AUTH="X-Access-Token: $TOKEN"

say() { echo; echo "===== $* =====" | tee -a "$OUT"; }
show() { tee -a "$OUT"; }

: > "$OUT"
say "0. 登录（token 前 40 字符）"
echo "${TOKEN:0:40}..." | show

say "1. 创建标签「冒烟紧急」"
LABEL=$(curl -s -X POST $BASE/team/label/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"冒烟紧急","color":"#d73a49"}')
echo "$LABEL" | show
LABEL_ID=$(curl -s "$BASE/team/label/list?pageNo=1&pageSize=100" -H "$AUTH" \
  | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['name']=='冒烟紧急'][0])")
echo "LABEL_ID=$LABEL_ID" | show

say "2. 建任务 A（被依赖方）与 B（依赖方）"
curl -s -X POST $BASE/team/task/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"P1冒烟-任务A（被依赖）"}' | show
curl -s -X POST $BASE/team/task/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"P1冒烟-任务B（依赖A）"}' | show
TASKS=$(curl -s "$BASE/team/task/list?pageNo=1&pageSize=10" -H "$AUTH")
TASK_A=$(echo "$TASKS" | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['title']=='P1冒烟-任务A（被依赖）'][0])")
TASK_B=$(echo "$TASKS" | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['title']=='P1冒烟-任务B（依赖A）'][0])")
echo "TASK_A=$TASK_A  TASK_B=$TASK_B" | show

say "3. B 依赖 A（期望 B blocked=1）"
curl -s -X POST $BASE/team/task/dependency -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_B\",\"dependsOnTaskId\":\"$TASK_A\"}" | show
curl -s "$BASE/team/task/queryById?id=$TASK_B" -H "$AUTH" \
  | python -c "import sys,json;t=json.load(sys.stdin)['result'];print('B.status=',t['status'],' B.blocked=',t['blocked'])" | show

say "4. 负路径：B 进 doing 应被拒「存在未完成依赖」"
curl -s -X PUT "$BASE/team/task/status?id=$TASK_B&status=doing" -H "$AUTH" | show
curl -s "$BASE/team/task/queryById?id=$TASK_B" -H "$AUTH" \
  | python -c "import sys,json;t=json.load(sys.stdin)['result'];print('拦截后 B.status=',t['status'],' B.blocked=',t['blocked'])" | show

say "5. 重复依赖应被拒"
curl -s -X POST $BASE/team/task/dependency -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_B\",\"dependsOnTaskId\":\"$TASK_A\"}" | show

say "6. A 完成 → B blocked 解除（期望 blocked=0）"
curl -s -X PUT "$BASE/team/task/status?id=$TASK_A&status=done" -H "$AUTH" | show
curl -s "$BASE/team/task/queryById?id=$TASK_B" -H "$AUTH" \
  | python -c "import sys,json;t=json.load(sys.stdin)['result'];print('A 完成后 B.blocked=',t['blocked'])" | show

say "7. B 认领（assignee=admin）+ 改派给 jeecg"
curl -s -X POST $BASE/team/task/claim -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"id\":\"$TASK_B\"}" | show
JEECG_ID=$(curl -s "$BASE/sys/user/list?username=jeecg&pageNo=1&pageSize=1" -H "$AUTH" \
  | python -c "import sys,json;print(json.load(sys.stdin)['result']['records'][0]['id'])")
curl -s -X POST $BASE/team/task/reassign -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"id\":\"$TASK_B\",\"assigneeId\":\"$JEECG_ID\",\"assigneeName\":\"jeecg\"}" | show

say "8. B 设置标签 + /team/task/list 并入 labels 验证"
curl -s -X POST $BASE/team/task/labels -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_B\",\"labelIds\":[\"$LABEL_ID\"]}" | show
curl -s "$BASE/team/task/list?pageNo=1&pageSize=10" -H "$AUTH" \
  | python -c "
import sys,json
d=json.load(sys.stdin)['result']['records']
b=[r for r in d if '任务B' in r['title']][0]
print('B.labels=',json.dumps(b.get('labels'),ensure_ascii=False),' B.tag=',repr(b.get('tag')))" | show

say "9. 建 Bug（期望服务端回填 bugNo）"
curl -s -X POST $BASE/team/bug/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"description":"P1冒烟-bug按钮错位"}' | show
BUG_ID=$(curl -s "$BASE/team/bug/list?pageNo=1&pageSize=1" -H "$AUTH" \
  | python -c "import sys,json;r=json.load(sys.stdin)['result']['records'][0];print(r['id'])")
curl -s "$BASE/team/bug/queryById?id=$BUG_ID" -H "$AUTH" \
  | python -c "import sys,json;b=json.load(sys.stdin)['result'];print('bug.bugNo=',b['bugNo'],' bug.status=',b['status'])" | show

say "10. 提审批单（OA 单号入聚合）"
curl -s -X POST $BASE/team/approval/submit -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"approval":{"category":"加班申请","title":"P1冒烟-周末加班","reason":"冒烟验证","startDate":"2026-11-29","endDate":"2026-11-30","formData":"{\"overtimeType\":\"周末\",\"hours\":4}"},"steps":[{"approverName":"管理员","approverRole":"系统管理员"}]}' | show

say "11. 聚合端点 kind=all（三源混合一页）"
curl -s "$BASE/team/issue/list?kind=all&state=all&pageNo=1&pageSize=10" -H "$AUTH" \
  | python -c "
import sys,json
p=json.load(sys.stdin)['result']
print('total=',p['total'])
for r in p['records']:
    print(' ',r['kind'],r['code'],'|',r['state'],'|blocked=',r['blocked'],'|assignee=',r['assigneeName'],'|labels=',len(r['labels']),'|',r['title'][:24])" | show

say "12. 聚合 keyword 过滤（T-/B-/OA- 各命中一次）"
curl -s "$BASE/team/issue/list?keyword=T-&pageNo=1&pageSize=5" -H "$AUTH" \
  | python -c "import sys,json;p=json.load(sys.stdin)['result'];print('keyword=T- total=',p['total'],'kinds=',sorted({r['kind'] for r in p['records']}))" | show
curl -s "$BASE/team/issue/list?keyword=B-&pageNo=1&pageSize=5" -H "$AUTH" \
  | python -c "import sys,json;p=json.load(sys.stdin)['result'];print('keyword=B- total=',p['total'],'codes=',[r['code'] for r in p['records']])" | show

say "13. 聚合 state=open/closed 过滤"
curl -s "$BASE/team/issue/list?state=open&pageNo=1&pageSize=20" -H "$AUTH" \
  | python -c "import sys,json;p=json.load(sys.stdin)['result'];print('state=open total=',p['total'],'states=',{r['state'] for r in p['records']})" | show
curl -s "$BASE/team/issue/list?state=closed&pageNo=1&pageSize=20" -H "$AUTH" \
  | python -c "import sys,json;p=json.load(sys.stdin)['result'];print('state=closed total=',p['total'],'states=',{r['state'] for r in p['records']})" | show

say "14. 清理：移除依赖/标签/删任务/删bug（迁移不物理删的反向——测试数据走逻辑删）"
curl -s -X DELETE "$BASE/team/task/dependency?taskId=$TASK_B&dependsOnTaskId=$TASK_A" -H "$AUTH" | show
curl -s -X POST $BASE/team/task/labels -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_B\",\"labelIds\":[]}" | show
curl -s -X DELETE "$BASE/team/task/delete?id=$TASK_B" -H "$AUTH" | show
curl -s -X DELETE "$BASE/team/task/delete?id=$TASK_A" -H "$AUTH" | show
curl -s -X DELETE "$BASE/team/bug/delete?id=$BUG_ID" -H "$AUTH" | show
curl -s -X DELETE "$BASE/team/label/delete?id=$LABEL_ID" -H "$AUTH" | show

say "冒烟完成"
