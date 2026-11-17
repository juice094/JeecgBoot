<script setup lang="ts">
// Bug 反馈（工具组）—— owner: juice094
// 数据：/team/bug/add 提交、/team/bug/list 列表、/team/bug/status 状态机流转
import { onMounted, reactive, ref, watch } from 'vue';
import { addBug, listBugs, changeBugStatus, type Bug } from '@/api/team';
import { BUG_STATUSES, BUG_TRANSITIONS, BUG_STATUS_TAG, type BugStatusKey } from '@/constants/bug';

const form = reactive({ desc: '' });
const submitting = ref(false);
const feedback = ref('');

async function submit() {
  if (!form.desc.trim() || submitting.value) return;
  submitting.value = true;
  feedback.value = '';
  try {
    await addBug(form.desc.trim());
    feedback.value = '反馈已提交，状态 open，可在下方列表跟踪处理进度';
    form.desc = '';
    await load();
  } catch (e: any) {
    feedback.value = e.message || '提交失败';
  } finally {
    submitting.value = false;
  }
}

// ---- 列表与状态流转 ----
type Tab = 'all' | BugStatusKey;
const activeTab = ref<Tab>('all');
const rows = ref<Bug[]>([]);
const loading = ref(false);
const errorMsg = ref('');
const busyId = ref('');

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const params: Record<string, unknown> = { pageNo: 1, pageSize: 50 };
    if (activeTab.value !== 'all') params.status = activeTab.value;
    const data = await listBugs(params);
    rows.value = data.result.records;
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

watch(activeTab, load);
onMounted(load);

function nextActions(status: string): { key: BugStatusKey; title: string }[] {
  const targets = (BUG_TRANSITIONS as Record<string, BugStatusKey[]>)[status] || [];
  return targets.map((key) => BUG_STATUSES.find((s) => s.key === key)!);
}

async function transit(row: Bug, target: BugStatusKey) {
  busyId.value = row.id;
  errorMsg.value = '';
  try {
    await changeBugStatus(row.id, target);
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '操作失败';
  } finally {
    busyId.value = '';
  }
}
</script>
<template>
  <div class="card" style="max-width: 900px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">系统 Bug 反馈</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px; float: right">{{ errorMsg }}</span>
    </div>
    <div class="form-group">
      <div class="form-label">问题描述 <span class="req">*</span></div>
      <textarea v-model="form.desc" class="form-control" placeholder="请详细描述您遇到的问题或功能异常..."></textarea>
    </div>
    <div class="form-group">
      <div class="form-label">截图附件</div>
      <div class="upload-box" style="padding: 15px">
        <p>点击上传截图（文件存储 MinIO 接入后启用，M2）</p>
      </div>
    </div>
    <button class="btn btn-primary" style="width: 100%" :disabled="submitting" @click="submit">
      {{ submitting ? '提交中…' : '提交反馈' }}
    </button>
    <p v-if="feedback" class="feedback">{{ feedback }}</p>

    <div class="tabs">
      <div class="tab-item" :class="{ active: activeTab === 'all' }" @click="activeTab = 'all'">全部</div>
      <div v-for="s in BUG_STATUSES" :key="s.key" class="tab-item" :class="{ active: activeTab === s.key }" @click="activeTab = s.key">
        {{ s.title }}
      </div>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>问题描述</th><th>提交人</th><th>状态</th><th>提交时间</th><th>流转</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">暂无数据</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td style="max-width: 320px">{{ row.description }}</td>
            <td>{{ row.reporterName || '-' }}</td>
            <td><span class="tag" :class="BUG_STATUS_TAG[row.status as BugStatusKey]">{{ BUG_STATUSES.find(s => s.key === row.status)?.title || row.status }}</span></td>
            <td>{{ row.createTime || '-' }}</td>
            <td>
              <template v-if="nextActions(row.status).length">
                <button v-for="a in nextActions(row.status)" :key="a.key" class="btn btn--sm" :disabled="busyId === row.id" @click="transit(row, a.key)">
                  → {{ a.title }}
                </button>
              </template>
              <span v-else style="color: var(--text-muted); font-size: 12px">已归档</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
<style scoped>
.feedback {
  color: var(--success);
  font-size: 13px;
  margin-top: 12px;
}
.tabs {
  display: flex;
  gap: 8px;
  margin: 20px 0 12px;
}
.tab-item {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: var(--font-size-sm);
  color: var(--text-regular);
}
.tab-item:hover {
  background: var(--bg-body);
}
.tab-item.active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 500;
}
.table-wrap {
  margin-top: 8px;
}
</style>