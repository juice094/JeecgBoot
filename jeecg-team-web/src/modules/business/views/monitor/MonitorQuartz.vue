<script setup lang="ts">
// 定时任务（监控组 · A 档首批）—— owner: showcase-dev
// 数据：jeecg 原生 /sys/quartzJob/**；2026-11-20 从只读升级为操作闭环：
//   新增/编辑（FormModal）+ 暂停/启动/立即执行/删除，按钮按 system:quartzJob:* 权限码显隐
import { computed, onMounted, reactive, ref } from 'vue';
import {
  listQuartzJobs, saveQuartzJob, deleteQuartzJob, controlQuartzJob, type QuartzJobRow,
} from '@/api/monitor';
import { usePermission } from '@/utils/permission';
import FormModal, { type FormField } from '@/components/FormModal.vue';

const PAGE_SIZE = 10;
const filter = reactive({ jobClassName: '' });
const pageNo = ref(1);
const total = ref(0);
const rows = ref<QuartzJobRow[]>([]);
const loading = ref(false);
const busyId = ref('');
const errorMsg = ref('');
const okMsg = ref('');

const { hasPermission } = usePermission();
const canAdd = hasPermission('system:quartzJob:add');
const canEdit = hasPermission('system:quartzJob:edit');
const canDelete = hasPermission('system:quartzJob:delete');
const canPause = hasPermission('system:quartzJob:pause');
const canResume = hasPermission('system:quartzJob:resume');
const canExecute = hasPermission('system:quartzJob:execute');
const canOperate = canPause || canResume || canExecute || canDelete;

function checkOk(res: any, fallback: string) {
  if (res && res.success === false) throw new Error(res.message || fallback);
}

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await listQuartzJobs({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      jobClassName: filter.jobClassName || undefined,
    });
    rows.value = data.result.records;
    total.value = data.result.total;
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  pageNo.value = 1;
  load();
}

function go(delta: number) {
  const next = pageNo.value + delta;
  if (next < 1 || (delta > 0 && rows.value.length === 0)) return;
  pageNo.value = next;
  load();
}

const totalPages = () => Math.max(1, Math.ceil(total.value / PAGE_SIZE));

async function runOp(row: QuartzJobRow, fn: () => Promise<any>, okText: string) {
  busyId.value = row.id;
  errorMsg.value = '';
  okMsg.value = '';
  try {
    checkOk(await fn(), okText + '失败');
    okMsg.value = okText + '成功';
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || okText + '失败';
  } finally {
    busyId.value = '';
  }
}

const pauseJob = (row: QuartzJobRow) => runOp(row, () => controlQuartzJob('pause', row.id), '暂停');
const resumeJob = (row: QuartzJobRow) => runOp(row, () => controlQuartzJob('resume', row.id), '启动');
const executeJob = (row: QuartzJobRow) => {
  if (!window.confirm('立即执行任务「' + (row.description || row.jobClassName) + '」？')) return;
  return runOp(row, () => controlQuartzJob('execute', row.id), '立即执行');
};
const removeJob = (row: QuartzJobRow) => {
  if (!window.confirm('确认删除任务「' + (row.description || row.jobClassName) + '」？此操作不可恢复。')) return;
  return runOp(row, () => deleteQuartzJob(row.id), '删除');
};

/* ---------------- 新增/编辑 ---------------- */
const jobModalOpen = ref(false);
const jobFormLoading = ref(false);
const editingJob = ref<QuartzJobRow | null>(null);

const jobFields = computed<FormField[]>(() => [
  { field: 'jobClassName', label: '任务类名', type: 'input', required: true, placeholder: '如 org.jeecg.modules.xxx.XxxJob' },
  { field: 'cronExpression', label: 'Cron 表达式', type: 'input', required: true, placeholder: '如 0 0 1 * * ?' },
  { field: 'parameter', label: '参数', type: 'input', placeholder: '可为空' },
  { field: 'description', label: '描述', type: 'input', placeholder: '可为空' },
]);

const jobInitialValues = computed<Record<string, any>>(() => ({
  jobClassName: editingJob.value?.jobClassName ?? '',
  cronExpression: editingJob.value?.cronExpression ?? '',
  parameter: editingJob.value?.parameter ?? '',
  description: editingJob.value?.description ?? '',
}));

function openAdd() {
  editingJob.value = null;
  jobModalOpen.value = true;
}

function openEdit(row: QuartzJobRow) {
  editingJob.value = row;
  jobModalOpen.value = true;
}

async function submitJob(values: Record<string, any>) {
  jobFormLoading.value = true;
  try {
    const payload: Record<string, any> = { ...values };
    for (const k of Object.keys(payload)) {
      if (payload[k] === '' || payload[k] === null || payload[k] === undefined) delete payload[k];
    }
    if (editingJob.value) payload.id = editingJob.value.id;
    checkOk(await saveQuartzJob(payload as QuartzJobRow, !!editingJob.value), '保存失败');
    jobModalOpen.value = false;
    okMsg.value = '';
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '保存失败';
  } finally {
    jobFormLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="card" style="max-width: 1100px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">定时任务</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px; float: right">{{ errorMsg }}</span>
      <span v-else-if="okMsg" style="color: var(--success); font-size: 13px; float: right">{{ okMsg }}</span>
    </div>
    <div class="filter-bar">
      <input v-model="filter.jobClassName" class="form-control" placeholder="按任务类名过滤" @keyup.enter="search" />
      <button class="btn btn-primary" :disabled="loading" @click="search">查询</button>
      <button v-if="canAdd" class="btn" style="margin-left: auto" :disabled="loading" @click="openAdd">新增任务</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>任务类名</th><th>Cron 表达式</th><th>参数</th><th>描述</th><th>运行状态</th><th v-if="canOperate">操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="canOperate ? 6 : 5" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td :colspan="canOperate ? 6 : 5" style="text-align: center; color: var(--text-muted)">暂无数据</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td style="max-width: 280px">{{ row.jobClassName || '-' }}</td>
            <td><code>{{ row.cronExpression || '-' }}</code></td>
            <td style="max-width: 160px">{{ row.parameter || '-' }}</td>
            <td style="max-width: 200px">{{ row.description || '-' }}</td>
            <td>
              <span v-if="row.status != null" class="tag" :class="row.status === 1 ? 'tag-success' : 'tag-danger'">
                {{ row.status === 1 ? '运行中' : '已停止' }}
              </span>
              <span v-else>-</span>
            </td>
            <td v-if="canOperate">
              <button v-if="row.status === 1 && canPause" class="btn btn--xs" :disabled="busyId === row.id" @click="pauseJob(row)">暂停</button>
              <button v-if="row.status !== 1 && canResume" class="btn btn--xs" :disabled="busyId === row.id" @click="resumeJob(row)">启动</button>
              <button v-if="canExecute" class="btn btn--xs" :disabled="busyId === row.id" @click="executeJob(row)">执行</button>
              <button v-if="canEdit" class="btn btn--xs" :disabled="busyId === row.id" @click="openEdit(row)">编辑</button>
              <button v-if="canDelete" class="btn btn--xs btn-text" :disabled="busyId === row.id" @click="removeJob(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <button class="btn btn--sm" :disabled="pageNo <= 1 || loading" @click="go(-1)">上一页</button>
      <span class="pager-info">第 {{ pageNo }} / {{ totalPages() }} 页 · 共 {{ total }} 条</span>
      <button class="btn btn--sm" :disabled="pageNo >= totalPages() || loading" @click="go(1)">下一页</button>
    </div>

    <FormModal
      v-model="jobModalOpen"
      :title="editingJob ? '编辑任务' : '新增任务'"
      :fields="jobFields"
      :initial-values="jobInitialValues"
      :loading="jobFormLoading"
      @submit="submitJob"
    />
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.filter-bar .form-control {
  max-width: 320px;
}
.table-wrap {
  margin-top: 8px;
}
.pager {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 12px;
}
.pager-info {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
td .btn--xs {
  margin-right: 6px;
}
</style>
