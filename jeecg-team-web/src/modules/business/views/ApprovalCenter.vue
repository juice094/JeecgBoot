<script setup lang="ts">
// 审批中心（工具组）—— owner: juice094
// 数据：/team/approval/list 真实接口；tab=待办(pending)/已办(approved+rejected)/我发起的(applicantId=本人)
import { onMounted, reactive, ref, watch } from 'vue';
import { useAuthStore } from '@/stores/auth';
import {
  listApprovals, listApprovalTodos, approveApproval, rejectApproval, fetchApprovalSteps,
  type Approval, type ApprovalStep,
} from '@/api/team';

type Tab = 'todo' | 'done' | 'mine';
const activeTab = ref<Tab>('todo');
const auth = useAuthStore();

const tabs: { key: Tab; label: string }[] = [
  { key: 'todo', label: '待我审批' },
  { key: 'done', label: '我已审批' },
  { key: 'mine', label: '我发起的' },
];

const rows = ref<Approval[]>([]);
const loading = ref(false);
const errorMsg = ref('');
const busyId = ref('');

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    if (activeTab.value === 'todo') {
      // 待我审批：后端两步走（wait 步骤按登录人姓名 + pending/currentStep 匹配），与守卫口径一致
      const data = await listApprovalTodos({ pageNo: 1, pageSize: 50 });
      rows.value = data.result.records;
      return;
    }
    const params: Record<string, unknown> = { pageNo: 1, pageSize: 50 };
    if (activeTab.value === 'mine') params.applicantId = auth.userInfo.id;
    const data = await listApprovals(params);
    let list = data.result.records;
    if (activeTab.value === 'done') list = list.filter((r) => r.status !== 'pending');
    rows.value = list;
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

watch(activeTab, load);
onMounted(load);

async function approve(row: Approval) {
  busyId.value = row.id;
  try {
    await approveApproval(row.id);
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '操作失败';
  } finally {
    busyId.value = '';
  }
}

async function reject(row: Approval) {
  const comment = window.prompt('拒绝理由（可空）') ?? '';
  busyId.value = row.id;
  try {
    await rejectApproval(row.id, comment || undefined);
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '操作失败';
  } finally {
    busyId.value = '';
  }
}

const STATUS_TAG: Record<Approval['status'], { text: string; cls: string }> = {
  pending: { text: '审批中', cls: 'tag-warning' },
  approved: { text: '已通过', cls: 'tag-success' },
  rejected: { text: '已拒绝', cls: 'tag-danger' },
};

// ---- 审批步骤时间线（行内展开，懒加载 + 缓存） ----
const STEP_ACTION: Record<ApprovalStep['action'], { text: string; cls: string }> = {
  wait: { text: '待审批', cls: 'tag-info' },
  approved: { text: '已通过', cls: 'tag-success' },
  rejected: { text: '已拒绝', cls: 'tag-danger' },
};
const expandedId = ref('');
const stepsCache = reactive<Record<string, ApprovalStep[]>>({});
const stepsLoading = ref('');

async function toggleSteps(row: Approval) {
  if (expandedId.value === row.id) {
    expandedId.value = '';
    return;
  }
  expandedId.value = row.id;
  if (stepsCache[row.id]) return;
  stepsLoading.value = row.id;
  try {
    const data = await fetchApprovalSteps(row.id);
    stepsCache[row.id] = data.result;
  } catch (e: any) {
    errorMsg.value = e.message || '步骤加载失败';
  } finally {
    stepsLoading.value = '';
  }
}
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">审批中心 / 待办事项</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px">{{ errorMsg }}</span>
    </div>
    <div class="tabs">
      <div v-for="t in tabs" :key="t.key" class="tab-item" :class="{ active: activeTab === t.key }" @click="activeTab = t.key">
        {{ t.label }} <span class="num">{{ t.key === activeTab ? rows.length : '' }}</span>
      </div>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>申请单号</th><th>申请人</th><th>申请类型</th><th>事项标题</th><th>提交时间</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="7" style="text-align: center; color: var(--text-muted)">暂无数据</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>{{ row.code }}</td>
            <td>{{ row.applicantName || '-' }}</td>
            <td>{{ row.category }}</td>
            <td>
              <span style="cursor: pointer; color: var(--primary)" @click="toggleSteps(row)">
                {{ row.title }}
                <span style="font-size: 12px">{{ expandedId === row.id ? '▲' : '▼' }}</span>
              </span>
            </td>
            <td>{{ row.createTime || '-' }}</td>
            <td><span class="tag" :class="STATUS_TAG[row.status].cls">{{ STATUS_TAG[row.status].text }}</span></td>
            <td>
              <template v-if="row.status === 'pending' && activeTab !== 'mine'">
                <button class="btn btn-primary btn--sm" :disabled="busyId === row.id" @click="approve(row)">通过</button>
                <button class="btn btn--sm btn--danger" :disabled="busyId === row.id" @click="reject(row)">拒绝</button>
              </template>
              <span v-else style="color: var(--text-muted); font-size: 12px">—</span>
            </td>
          </tr>
          <tr v-if="expandedId" :key="`steps-${expandedId}`" class="steps-row">
            <td colspan="7">
              <div v-if="stepsLoading === expandedId" style="color: var(--text-muted); font-size: 13px">步骤加载中…</div>
              <div v-else-if="stepsCache[expandedId]" class="steps-timeline">
                <div v-for="step in stepsCache[expandedId]" :key="step.id" class="step-item">
                  <span class="step-no">{{ step.stepNo }}</span>
                  <span class="step-approver">{{ step.approverName }}<span v-if="step.approverRole" class="step-role">（{{ step.approverRole }}）</span></span>
                  <span class="tag" :class="STEP_ACTION[step.action].cls">{{ STEP_ACTION[step.action].text }}</span>
                  <span v-if="step.comment" class="step-comment">“{{ step.comment }}”</span>
                  <span class="step-time">{{ step.actedAt || '' }}</span>
                </div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.btn--sm {
  padding: 4px 10px;
  font-size: var(--font-size-sm);
  margin-right: 6px;
}
.btn--danger {
  background: transparent;
  border: 1px solid var(--danger);
  color: var(--danger);
}
.btn--danger:hover {
  background: var(--danger);
  color: #fff;
}
.steps-row td {
  background: var(--bg-body);
  padding: 12px 16px;
}
.steps-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: var(--font-size-sm);
}
.step-no {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--primary-light);
  color: var(--primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.step-role,
.step-time {
  color: var(--text-muted);
}
.step-comment {
  color: var(--text-regular);
  font-style: italic;
}
</style>
