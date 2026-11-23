<script setup lang="ts">
// 流程中心（工具组）—— owner: juice094
// tab 口径：发起流程（/flow/process/list 定义清单 + 动态表单）｜我的待办（/flow/approve/todo）｜我的已办（/flow/approve/done）
// 契约源：FlowProcessController（W2）；请假发起走 /flow/demo/leave/start（W1 冒烟端点，W2 未覆盖，见 api/flow.ts 注）
// 校验口径与后端 FlowExpenseService / FlowLeaveService 一致：金额 >0、≤100万、≤2 位小数；事由必填 ≤500 字；
// 附件说明 ≤500 字；办理人必填；请假天数整数 1-365。
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { PhX } from '@phosphor-icons/vue';
import { useAuthStore } from '@/stores/auth';
import { usePermission } from '@/utils/permission';
import {
  fetchProcessList, startExpenseProcess, startLeaveProcess,
  fetchTodoList, fetchDoneList, handleApproveTask, fetchInstanceDetail,
  type FlowProcessDef, type FlowTodoTask, type FlowDoneTask,
  type FlowInstanceDetail, type FlowApproveAction,
} from '@/api/flow';

const auth = useAuthStore();
const { hasPermission } = usePermission();
// 细控：发起按 process:start；办理按 approve:handle；轨迹按 instance:detail（页级入口由路由 meta.perm 把控）
const canStart = hasPermission('team:flow:process:start');
const canHandle = hasPermission('team:flow:approve:handle');
const canDetail = hasPermission('team:flow:instance:detail');
const canSeeDone = hasPermission('team:flow:approve:done');

type Tab = 'start' | 'todo' | 'done';
const activeTab = ref<Tab>('start');
const tabs = computed(() => {
  const all: { key: Tab; label: string }[] = [
    { key: 'start', label: '发起流程' },
    { key: 'todo', label: '我的待办' },
  ];
  if (canSeeDone) all.push({ key: 'done', label: '我的已办' });
  return all;
});

const errorMsg = ref('');

// ---- Tab1 发起流程 ----
const defs = ref<FlowProcessDef[]>([]);
const defsLoading = ref(false);
const selectedKey = ref('');
const selectedDef = computed(() => defs.value.find((d) => d.processDefinitionKey === selectedKey.value));

const form = reactive({ amount: '', reason: '', attachmentNote: '', deptManager: '', vp: '', days: '' });
const errors = reactive<Record<string, string>>({});
const starting = ref(false);
const startError = ref('');
const receiptId = ref('');

const isExpense = computed(() => selectedKey.value === 'expenseProcess');
const isLeave = computed(() => selectedKey.value === 'leaveProcess');
const isSupported = computed(() => isExpense.value || isLeave.value);

async function loadDefs() {
  defsLoading.value = true;
  try {
    const data = await fetchProcessList();
    defs.value = data.result ?? [];
    if (!selectedKey.value && defs.value.length) selectedKey.value = defs.value[0].processDefinitionKey;
  } catch (e: any) {
    errorMsg.value = e.message || '流程定义加载失败';
  } finally {
    defsLoading.value = false;
  }
}

function selectDef(key: string) {
  if (key === selectedKey.value) return;
  selectedKey.value = key;
  startError.value = '';
  receiptId.value = '';
}

const MAX_AMOUNT = 1000000;
const AMOUNT_PATTERN = /^(0|[1-9]\d*)(\.\d{1,2})?$/;
const MAX_TEXT = 500;
const MAX_LEAVE_DAYS = 365;
const MIN_LEAVE_DAYS = 1;

function validateStart(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k]);
  if (!isSupported.value) {
    startError.value = '该流程类型暂不支持在线发起';
    return false;
  }
  if (isExpense.value) {
    const amountText = form.amount.trim();
    if (!amountText) errors.amount = '请填写报销金额';
    else if (!AMOUNT_PATTERN.test(amountText)) errors.amount = '金额格式不正确（最多两位小数）';
    else if (Number(amountText) <= 0) errors.amount = '报销金额必须大于 0';
    else if (Number(amountText) > MAX_AMOUNT) errors.amount = '报销金额不能超过 1,000,000';
    if (!form.reason.trim()) errors.reason = '请填写报销事由';
    else if (form.reason.length > MAX_TEXT) errors.reason = `报销事由不能超过 ${MAX_TEXT} 字`;
    if (form.attachmentNote.length > MAX_TEXT) errors.attachmentNote = `附件说明不能超过 ${MAX_TEXT} 字`;
    if (!form.deptManager.trim()) errors.deptManager = '请填写部门主管办理人';
    if (!form.vp.trim()) errors.vp = '请填写副总办理人';
  }
  if (isLeave.value) {
    const daysText = form.days.trim();
    if (!daysText) errors.days = '请填写请假天数';
    else if (!/^\d+$/.test(daysText)) errors.days = '请假天数必须为整数';
    else if (Number(daysText) < MIN_LEAVE_DAYS || Number(daysText) > MAX_LEAVE_DAYS)
      errors.days = `请假天数必须在 ${MIN_LEAVE_DAYS}-${MAX_LEAVE_DAYS} 之间`;
    if (form.reason.length > MAX_TEXT) errors.reason = `请假事由不能超过 ${MAX_TEXT} 字`;
    const applicant = auth.userInfo.username?.trim();
    if (!applicant) errors.days = errors.days || '当前登录人信息缺失，无法发起';
  }
  return Object.keys(errors).length === 0;
}

/** 从后端回执「流程实例启动成功: <id>」中提取实例号 */
function extractInstanceId(message: string): string {
  const m = message.match(/:\s*(\S+)\s*$/);
  return m ? m[1] : message;
}

async function submitStart() {
  startError.value = '';
  receiptId.value = '';
  if (!validateStart()) return;
  starting.value = true;
  try {
    if (isExpense.value) {
      const data = await startExpenseProcess({
        amount: Number(form.amount.trim()),
        reason: form.reason.trim(),
        attachmentNote: form.attachmentNote.trim() || undefined,
        deptManager: form.deptManager.trim(),
        vp: form.vp.trim(),
      });
      receiptId.value = extractInstanceId(data.result);
    } else {
      const data = await startLeaveProcess({
        applicant: auth.userInfo.username!.trim(),
        days: Number(form.days.trim()),
        reason: form.reason.trim() || undefined,
      });
      receiptId.value = extractInstanceId(data.result);
    }
    form.amount = '';
    form.reason = '';
    form.attachmentNote = '';
    form.deptManager = '';
    form.vp = '';
    form.days = '';
  } catch (e: any) {
    startError.value = e.message || '提交失败';
  } finally {
    starting.value = false;
  }
}

// ---- Tab2/Tab3 待办 / 已办 ----
const todoRows = ref<FlowTodoTask[]>([]);
const doneRows = ref<FlowDoneTask[]>([]);
const listLoading = ref(false);
const busyTaskId = ref('');

async function load() {
  errorMsg.value = '';
  if (activeTab.value === 'start') return;
  listLoading.value = true;
  try {
    if (activeTab.value === 'todo') {
      const data = await fetchTodoList();
      todoRows.value = data.result ?? [];
    } else {
      const data = await fetchDoneList();
      doneRows.value = data.result ?? [];
    }
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    listLoading.value = false;
  }
}

watch(activeTab, load);
onMounted(() => {
  void loadDefs();
  void load();
});

// ---- 办理弹窗（通过/驳回 + 意见，在途锁防重复提交） ----
const handleVisible = ref(false);
const handleTask = ref<FlowTodoTask | null>(null);
const handleAction = ref<FlowApproveAction>('approve');
const handleComment = ref('');
const handleError = ref('');

function openHandle(row: FlowTodoTask) {
  handleTask.value = row;
  handleAction.value = 'approve';
  handleComment.value = '';
  handleError.value = '';
  handleVisible.value = true;
}

async function submitHandle() {
  if (!handleTask.value || busyTaskId.value) return;
  busyTaskId.value = handleTask.value.taskId;
  handleError.value = '';
  try {
    await handleApproveTask(
      handleTask.value.taskId,
      handleAction.value,
      handleComment.value.trim() || undefined,
    );
    handleVisible.value = false;
    await load();
  } catch (e: any) {
    handleError.value = e.message || '办理失败';
  } finally {
    busyTaskId.value = '';
  }
}

// ---- 实例详情抽屉（进度 + 变量 + 活动/任务轨迹） ----
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<FlowInstanceDetail | null>(null);
const detailError = ref('');
const detailFor = ref('');

async function openDetail(processInstanceId: string) {
  detailFor.value = processInstanceId;
  detail.value = null;
  detailError.value = '';
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    const data = await fetchInstanceDetail(processInstanceId);
    detail.value = data.result;
  } catch (e: any) {
    detailError.value = e.message || '详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

function closeDetail() {
  detailVisible.value = false;
  detail.value = null;
}

/** 完结态视觉口径：endActivityId=rejectedEnd 驳回（danger）/ approvedEnd 通过（success），其余中性 */
function endTag(d: FlowInstanceDetail | null) {
  if (!d) return { text: '', cls: 'tag--neutral' };
  if (!d.finished) return { text: '进行中', cls: 'tag--warning' };
  if (d.endActivityId === 'rejectedEnd') return { text: '已驳回', cls: 'tag--danger' };
  if (d.endActivityId === 'approvedEnd') return { text: '已通过', cls: 'tag--success' };
  return { text: '已完结', cls: 'tag--neutral' };
}

/** 轨迹节点状态：已结束节点 success；在途节点 info；驳回/通过终结点按 endActivityId 区分 */
function nodeTag(node: { activityId?: string; endTime?: string }, d: FlowInstanceDetail | null) {
  if (node.endTime) {
    if (node.activityId === 'rejectedEnd') return { text: '驳回', cls: 'tag--danger' };
    if (node.activityId === 'approvedEnd') return { text: '通过', cls: 'tag--success' };
    return { text: '已完成', cls: 'tag--success' };
  }
  return { text: '进行中', cls: 'tag--info' };
}

function fmtTime(v?: string | number | null): string {
  if (!v) return '-';
  if (typeof v === 'number') {
    const d = new Date(v);
    return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleString('zh-CN', { hour12: false });
  }
  return String(v);
}

function fmtDuration(ms?: number | null): string {
  if (!ms || ms <= 0) return '-';
  const minutes = Math.floor(ms / 60000);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时 ${minutes % 60} 分钟`;
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`;
}

function variableEntries(d: FlowInstanceDetail | null): [string, unknown][] {
  if (!d?.variables) return [];
  return Object.entries(d.variables);
}
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">流程中心</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px">{{ errorMsg }}</span>
    </div>

    <div class="tabs">
      <div
        v-for="t in tabs"
        :key="t.key"
        class="tab-item"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        {{ t.label }}
        <span class="num">{{ t.key === activeTab && t.key !== 'start' ? (t.key === 'todo' ? todoRows.length : doneRows.length) : '' }}</span>
      </div>
    </div>

    <!-- Tab1 发起流程 -->
    <div v-if="activeTab === 'start'" class="start-layout">
      <div class="def-list">
        <div v-if="defsLoading" style="color: var(--text-muted); font-size: 13px">流程定义加载中…</div>
        <template v-else>
          <div
            v-for="d in defs"
            :key="d.processDefinitionKey"
            class="def-item"
            :class="{ 'def-item--active': selectedKey === d.processDefinitionKey }"
            @click="selectDef(d.processDefinitionKey)"
          >
            <div class="def-name">{{ d.processDefinitionName || d.processDefinitionKey }}</div>
            <div class="def-meta">
              <span class="tag tag--neutral">v{{ d.version ?? '-' }}</span>
              <span class="tag" :class="d.suspended ? 'tag--danger' : 'tag--info'">{{ d.suspended ? '已挂起' : '启用中' }}</span>
            </div>
            <div class="def-meta" style="color: var(--text-muted)">在途 {{ d.runningInstances ?? 0 }} 个实例</div>
          </div>
          <div v-if="!defs.length" style="color: var(--text-muted); font-size: 13px">暂无可用流程定义</div>
        </template>
      </div>

      <div class="start-form">
        <template v-if="selectedDef">
          <h3 class="form-title">{{ selectedDef.processDefinitionName || selectedDef.processDefinitionKey }}</h3>
          <p v-if="!isSupported" class="form-hint">该流程类型暂不支持在线发起（后端未提供发起端点）。</p>
          <p v-else-if="selectedDef.suspended" class="form-hint">该流程已挂起，暂不可发起。</p>

          <div v-if="isLeave" class="form-group">
            <label class="form-label"><span class="req">*</span>请假天数</label>
            <input v-model="form.days" class="form-control" type="number" min="1" :max="MAX_LEAVE_DAYS" placeholder="1-365 的整数" :disabled="starting || !!selectedDef.suspended" />
            <div v-if="errors.days" class="form-err">{{ errors.days }}</div>
          </div>

          <div v-if="isExpense" class="form-group">
            <label class="form-label"><span class="req">*</span>报销金额（元）</label>
            <input v-model="form.amount" class="form-control" type="number" min="0" step="0.01" placeholder="大于 0，不超过 1,000,000，最多两位小数" :disabled="starting || !!selectedDef.suspended" />
            <div v-if="errors.amount" class="form-err">{{ errors.amount }}</div>
          </div>

          <div class="form-group">
            <label class="form-label"><span v-if="isExpense" class="req">*</span>{{ isLeave ? '请假事由' : '事由' }}</label>
            <textarea v-model="form.reason" class="form-control" rows="3" maxlength="500" :placeholder="isLeave ? '可空，不超过 500 字' : '请填写事由，不超过 500 字'" :disabled="starting || !!selectedDef.suspended"></textarea>
            <div v-if="errors.reason" class="form-err">{{ errors.reason }}</div>
          </div>

          <template v-if="isExpense">
            <div class="form-group">
              <label class="form-label">附件说明</label>
              <textarea v-model="form.attachmentNote" class="form-control" rows="2" maxlength="500" placeholder="可空，不超过 500 字" :disabled="starting || !!selectedDef.suspended"></textarea>
              <div v-if="errors.attachmentNote" class="form-err">{{ errors.attachmentNote }}</div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label"><span class="req">*</span>部门主管办理人</label>
                <input v-model="form.deptManager" class="form-control" placeholder="登录账号（办理人链显式传入）" :disabled="starting || !!selectedDef.suspended" />
                <div v-if="errors.deptManager" class="form-err">{{ errors.deptManager }}</div>
              </div>
              <div class="form-group">
                <label class="form-label"><span class="req">*</span>副总办理人</label>
                <input v-model="form.vp" class="form-control" placeholder="登录账号（办理人链显式传入）" :disabled="starting || !!selectedDef.suspended" />
                <div v-if="errors.vp" class="form-err">{{ errors.vp }}</div>
              </div>
            </div>
          </template>

          <div v-if="startError" class="form-err" style="margin-bottom: 10px">{{ startError }}</div>
          <div v-if="receiptId" class="receipt">
            提交流程成功，流程实例号：<code>{{ receiptId }}</code>，可到「我的待办 / 轨迹」跟踪进度。
          </div>

          <button
            v-if="canStart"
            class="btn btn-primary"
            style="margin-top: 12px"
            :disabled="starting || !!selectedDef.suspended || !isSupported"
            @click="submitStart"
          >
            {{ starting ? '提交中…' : '提交申请' }}
          </button>
          <p v-else class="form-hint">当前账号无发起权限（team:flow:process:start）。</p>
        </template>
        <div v-else style="color: var(--text-muted); font-size: 13px">请选择左侧流程类型</div>
      </div>
    </div>

    <!-- Tab2 我的待办 -->
    <div v-else-if="activeTab === 'todo'" class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>任务</th><th>所属流程</th><th>流程实例号</th><th>到达时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="listLoading">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!todoRows.length">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">暂无待办</td>
          </tr>
          <tr v-for="row in todoRows" v-else :key="row.taskId">
            <td>{{ row.taskName || '-' }}</td>
            <td>{{ row.processDefinitionName || '-' }}</td>
            <td><code style="font-size: 12px">{{ row.processInstanceId || '-' }}</code></td>
            <td>{{ fmtTime(row.createTime) }}</td>
            <td>
              <button v-if="canHandle" class="btn btn-primary btn--sm" :disabled="busyTaskId === row.taskId" @click="openHandle(row)">办理</button>
              <button v-if="canDetail" class="btn btn--sm" style="margin-left: 6px" @click="openDetail(row.processInstanceId || '')">轨迹</button>
              <span v-if="!canHandle && !canDetail" style="color: var(--text-muted); font-size: 12px">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Tab3 我的已办 -->
    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>任务</th><th>所属流程</th><th>流程实例号</th><th>开始时间</th><th>完成时间</th><th>耗时</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="listLoading">
            <td colspan="7" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!doneRows.length">
            <td colspan="7" style="text-align: center; color: var(--text-muted)">暂无已办记录</td>
          </tr>
          <tr v-for="row in doneRows" v-else :key="row.taskId">
            <td>{{ row.taskName || '-' }}</td>
            <td>{{ row.processDefinitionName || '-' }}</td>
            <td><code style="font-size: 12px">{{ row.processInstanceId || '-' }}</code></td>
            <td>{{ fmtTime(row.startTime) }}</td>
            <td>{{ fmtTime(row.endTime) }}</td>
            <td>{{ fmtDuration(row.durationInMillis) }}</td>
            <td>
              <button v-if="canDetail" class="btn btn--sm" @click="openDetail(row.processInstanceId || '')">轨迹</button>
              <span v-else style="color: var(--text-muted); font-size: 12px">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 办理弹窗 -->
    <div v-if="handleVisible" class="fc-mask" @click.self="handleVisible = false">
      <div class="fc-dialog">
        <div class="fc-dialog__head">
          <h3 class="fc-dialog__title">办理任务：{{ handleTask?.taskName || '-' }}</h3>
          <button class="icon-btn" title="关闭" @click="handleVisible = false"><PhX :size="16" /></button>
        </div>
        <div class="fc-dialog__body">
          <div class="form-group">
            <label class="form-label">审批动作</label>
            <div class="action-row">
              <label class="action-option" :class="{ 'action-option--active': handleAction === 'approve' }">
                <input v-model="handleAction" type="radio" value="approve" :disabled="!!busyTaskId" /> 通过
              </label>
              <label class="action-option action-option--reject" :class="{ 'action-option--reject-active': handleAction === 'reject' }">
                <input v-model="handleAction" type="radio" value="reject" :disabled="!!busyTaskId" /> 驳回
              </label>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">审批意见</label>
            <textarea v-model="handleComment" class="form-control" rows="3" maxlength="500" placeholder="可空，不超过 500 字" :disabled="!!busyTaskId"></textarea>
          </div>
          <div v-if="handleError" class="form-err">{{ handleError }}</div>
        </div>
        <div class="fc-dialog__foot">
          <button class="btn btn-default" :disabled="!!busyTaskId" @click="handleVisible = false">取消</button>
          <button class="btn btn-primary" :disabled="!!busyTaskId" @click="submitHandle">
            {{ busyTaskId ? '办理中…' : '提交办理' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 实例详情抽屉 -->
    <div v-if="detailVisible" class="fc-drawer-mask" @click.self="closeDetail">
      <aside class="fc-drawer">
        <div class="fc-drawer__head">
          <div>
            <h3 class="fc-dialog__title">流程实例详情</h3>
            <span v-if="detail" class="tag" :class="endTag(detail).cls" style="margin-top: 6px">{{ endTag(detail).text }}</span>
          </div>
          <button class="icon-btn" title="关闭" @click="closeDetail"><PhX :size="16" /></button>
        </div>
        <div class="fc-drawer__body">
          <div v-if="detailLoading" style="color: var(--text-muted); font-size: 13px">详情加载中…</div>
          <div v-else-if="detailError" style="color: var(--danger); font-size: 13px">{{ detailError }}</div>
          <template v-else-if="detail">
            <section class="detail-section">
              <div class="detail-row"><span class="detail-key">流程</span><span>{{ detail.processDefinitionName || detail.processDefinitionKey || '-' }}</span></div>
              <div class="detail-row"><span class="detail-key">实例号</span><code style="font-size: 12px">{{ detail.processInstanceId }}</code></div>
              <div class="detail-row"><span class="detail-key">开始时间</span><span>{{ fmtTime(detail.startTime) }}</span></div>
              <div class="detail-row"><span class="detail-key">结束时间</span><span>{{ fmtTime(detail.endTime) }}</span></div>
              <div class="detail-row"><span class="detail-key">耗时</span><span>{{ fmtDuration(detail.durationInMillis) }}</span></div>
              <div v-if="detail.openTasks?.length" class="detail-row">
                <span class="detail-key">当前节点</span>
                <span>{{ detail.openTasks.join('、') }}</span>
              </div>
            </section>

            <section v-if="variableEntries(detail).length" class="detail-section">
              <h4 class="detail-title">流程变量</h4>
              <div v-for="[k, v] in variableEntries(detail)" :key="k" class="detail-row">
                <span class="detail-key">{{ k }}</span>
                <span>{{ typeof v === 'object' ? JSON.stringify(v) : String(v) }}</span>
              </div>
            </section>

            <section class="detail-section">
              <h4 class="detail-title">节点轨迹</h4>
              <div class="trace-list">
                <div v-for="(node, i) in detail.activityTrace ?? []" :key="`${node.activityId}-${i}`" class="trace-item">
                  <span class="trace-dot" :class="{ 'trace-dot--active': !node.endTime }"></span>
                  <div class="trace-main">
                    <div class="trace-line">
                      <span class="trace-name">{{ node.activityName || node.activityId || '-' }}</span>
                      <span class="tag" :class="nodeTag(node, detail).cls">{{ nodeTag(node, detail).text }}</span>
                    </div>
                    <div class="trace-meta">
                      <span v-if="node.assignee">办理人 {{ node.assignee }}</span>
                      <span>{{ fmtTime(node.startTime) }} ~ {{ node.endTime ? fmtTime(node.endTime) : '在途' }}</span>
                      <span v-if="node.durationInMillis">耗时 {{ fmtDuration(node.durationInMillis) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <section class="detail-section">
              <h4 class="detail-title">任务与意见</h4>
              <div class="trace-list">
                <div v-for="task in detail.taskTrace ?? []" :key="task.taskId" class="trace-item">
                  <span class="trace-dot trace-dot--task"></span>
                  <div class="trace-main">
                    <div class="trace-line">
                      <span class="trace-name">{{ task.taskName || '-' }}</span>
                      <span class="trace-meta">{{ task.assignee || '-' }}</span>
                    </div>
                    <div class="trace-meta">
                      <span>{{ fmtTime(task.startTime) }} ~ {{ task.endTime ? fmtTime(task.endTime) : '在途' }}</span>
                      <span v-if="task.deleteReason">（{{ task.deleteReason }}）</span>
                    </div>
                    <div v-for="(c, ci) in task.comments ?? []" :key="ci" class="trace-comment">“{{ c }}”</div>
                  </div>
                </div>
                <div v-if="!(detail.taskTrace ?? []).length" style="color: var(--text-muted); font-size: 13px">暂无任务记录</div>
              </div>
            </section>
          </template>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.start-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 20px;
  margin-top: 16px;
}
@media (max-width: 900px) {
  .start-layout {
    grid-template-columns: 1fr;
  }
}
.def-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.def-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.def-item:hover {
  border-color: var(--primary);
}
.def-item--active {
  border-color: var(--primary);
  background: var(--primary-50);
}
.def-name {
  font-weight: 600;
  color: var(--text-main);
  font-size: var(--font-size-md);
}
.def-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: var(--font-size-sm);
}
.form-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 12px;
}
.form-hint {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  margin-bottom: 10px;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.form-err {
  font-size: 12px;
  color: var(--danger);
  margin-top: 4px;
}
.receipt {
  background: var(--success-50);
  border: 1px solid var(--success-border);
  color: var(--success-700);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font-size: var(--font-size-sm);
}
.receipt code {
  font-weight: 600;
}
.fc-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.fc-dialog {
  width: 480px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
}
.fc-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}
.fc-dialog__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}
.fc-dialog__body {
  padding: 20px;
}
.fc-dialog__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border-color);
}
.action-row {
  display: flex;
  gap: 10px;
}
.action-option {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 0;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  color: var(--text-regular);
  cursor: pointer;
}
.action-option--active {
  border-color: var(--success);
  color: var(--success);
  background: var(--success-50);
}
.action-option--reject-active {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-50);
}
.fc-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 200;
  display: flex;
  justify-content: flex-end;
}
.fc-drawer {
  width: 520px;
  max-width: calc(100vw - 32px);
  height: 100%;
  background: var(--bg-card);
  border-left: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  animation: slideIn 0.18s var(--ease-out);
}
@keyframes slideIn {
  from {
    transform: translateX(32px);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}
.fc-drawer__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}
.fc-drawer__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.detail-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-regular);
  margin-bottom: 2px;
}
.detail-row {
  display: flex;
  gap: 12px;
  font-size: var(--font-size-sm);
  color: var(--text-main);
}
.detail-key {
  width: 64px;
  flex-shrink: 0;
  color: var(--text-muted);
}
.trace-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.trace-item {
  display: flex;
  gap: 10px;
}
.trace-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--success);
  margin-top: 5px;
  flex-shrink: 0;
}
.trace-dot--active {
  background: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 25%, transparent);
}
.trace-dot--task {
  background: var(--info);
}
.trace-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.trace-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.trace-name {
  font-size: var(--font-size-md);
  color: var(--text-main);
  font-weight: 500;
}
.trace-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 12px;
  color: var(--text-muted);
}
.trace-comment {
  font-size: var(--font-size-sm);
  color: var(--text-regular);
  font-style: italic;
}
.icon-btn {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-sm);
  display: inline-flex;
}
.icon-btn:hover {
  background: var(--gray-100);
  color: var(--text-main);
}
</style>
