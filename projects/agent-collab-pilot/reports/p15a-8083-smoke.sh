#!/bin/bash
# P1.5a 8083 冒烟（三块各一条请求级留证）：
#   ① /team/issue/list 聚合 task 行内嵌 blockedBy + /team/task/detail blockedBy
#   ② /flow/process/list 每项带 approverPreview{deptManager,vp}
#   ③ 删最大序号任务后新建 taskNo=MAX+1 不撞 uk_team_task_no
set -u
BASE=http://127.0.0.1:8083/jeecg-boot
OUT=/c/Users/22414/dev/JeecgBoot/projects/agent-collab-pilot/reports/p15a-8083-smoke.log

TOKEN=$(curl -s -X POST $BASE/sys/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","captcha":"x7yw","checkKey":"smoke-check-key"}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['result']['token'])")
AUTH="X-Access-Token: $TOKEN"

say() { echo; echo "===== $* =====" | tee -a "$OUT"; }
show() { tee -a "$OUT"; }

: > "$OUT"
say "0. 登录"
echo "${TOKEN:0:40}..." | show

say "1a. 建任务 C（被依赖）与 D（依赖方），D 依赖 C"
curl -s -X POST $BASE/team/task/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"P15a冒烟-被依赖C"}' | show
curl -s -X POST $BASE/team/task/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"P15a冒烟-依赖方D"}' | show
TASKS=$(curl -s "$BASE/team/task/list?pageNo=1&pageSize=100" -H "$AUTH")
TASK_C=$(echo "$TASKS" | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['title']=='P15a冒烟-被依赖C'][0])")
TASK_D=$(echo "$TASKS" | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['title']=='P15a冒烟-依赖方D'][0])")
echo "TASK_C=$TASK_C  TASK_D=$TASK_D" | show
curl -s -X POST $BASE/team/task/dependency -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_D\",\"dependsOnTaskId\":\"$TASK_C\"}" | show

say "1b. 聚合行 blockedBy：/team/issue/list kind=task（D 行应含 C id，C 行空数组）"
curl -s "$BASE/team/issue/list?kind=task&keyword=P15a&pageNo=1&pageSize=10" -H "$AUTH" \
  | python -c "
import sys,json
rows=json.load(sys.stdin)['result']['records']
for r in rows:
    print(r['title'], '->', 'blockedBy=', r['blockedBy'], 'blocked=', r['blocked'])" | show

say "1c. 详情 blockedBy：/team/task/detail?id=D"
curl -s "$BASE/team/task/detail?id=$TASK_D" -H "$AUTH" \
  | python -c "import sys,json;d=json.load(sys.stdin)['result'];print('D.blockedBy=',d['blockedBy'],' task.blocked=',d['task']['blocked'])" | show

say "2. /flow/process/list approverPreview（与 /tools/request 默认链同源）"
curl -s "$BASE/flow/process/list" -H "$AUTH" \
  | python -c "
import sys,json
for it in json.load(sys.stdin)['result']:
    print(it['processDefinitionKey'], '-> approverPreview=', it['approverPreview'])" | show

say "3. 删最大序号任务后重建不撞 uk_team_task_no"
curl -s -X POST $BASE/team/task/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"P15a冒烟-序号N1"}' | show
TASKS=$(curl -s "$BASE/team/task/list?pageNo=1&pageSize=100" -H "$AUTH")
TASK_N1=$(echo "$TASKS" | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['title']=='P15a冒烟-序号N1'][0])")
NO_N1=$(curl -s "$BASE/team/task/queryById?id=$TASK_N1" -H "$AUTH" \
  | python -c "import sys,json;print(json.load(sys.stdin)['result']['taskNo'])")
echo "N1 taskNo=$NO_N1" | show
curl -s -X DELETE "$BASE/team/task/delete?id=$TASK_N1" -H "$AUTH" | show
curl -s -X POST $BASE/team/task/add -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"title":"P15a冒烟-序号N2"}' | show
TASKS=$(curl -s "$BASE/team/task/list?pageNo=1&pageSize=100" -H "$AUTH")
TASK_N2=$(echo "$TASKS" | python -c "import sys,json;d=json.load(sys.stdin)['result']['records'];print([r['id'] for r in d if r['title']=='P15a冒烟-序号N2'][0])")
NO_N2=$(curl -s "$BASE/team/task/queryById?id=$TASK_N2" -H "$AUTH" \
  | python -c "import sys,json;print(json.load(sys.stdin)['result']['taskNo'])")
echo "N2 taskNo=$NO_N2（期望 = N1+1 = $((NO_N1+1))；旧口径会复用 $NO_N1 撞唯一键）" | show

say "4. 清理（逻辑删冒烟任务）"
for t in "$TASK_C" "$TASK_D" "$TASK_N2"; do
  curl -s -X DELETE "$BASE/team/task/delete?id=$t" -H "$AUTH" | show
done
say "冒烟完成"
