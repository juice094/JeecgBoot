<script setup lang="ts">
// 定时任务（监控组 · A 档首批）—— owner: showcase-dev
// 数据：jeecg 原生 GET /sys/quartzJob/list（只读口径；暂停/恢复/立即执行属写操作，本轮不开放，docs/24 备注）
import { onMounted, reactive, ref } from 'vue';
import { listQuartzJobs, type QuartzJobRow } from '@/api/monitor';

const PAGE_SIZE = 10;
const filter = reactive({ jobClassName: '' });
const pageNo = ref(1);
const total = ref(0);
const rows = ref<QuartzJobRow[]>([]);
const loading = ref(false);
const errorMsg = ref('');

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

onMounted(load);
</script>

<template>
  <div class="card" style="max-width: 1100px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">定时任务</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px; float: right">{{ errorMsg }}</span>
    </div>
    <div class="filter-bar">
      <input v-model="filter.jobClassName" class="form-control" placeholder="按任务类名过滤" @keyup.enter="search" />
      <button class="btn btn-primary" :disabled="loading" @click="search">查询</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>任务类名</th><th>Cron 表达式</th><th>参数</th><th>描述</th><th>运行状态</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">暂无数据</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td style="max-width: 320px">{{ row.jobClassName || '-' }}</td>
            <td><code>{{ row.cronExpression || '-' }}</code></td>
            <td style="max-width: 200px">{{ row.parameter || '-' }}</td>
            <td style="max-width: 240px">{{ row.description || '-' }}</td>
            <td>
              <span v-if="row.status != null" class="tag" :class="row.status === 1 ? 'tag-success' : 'tag-danger'">
                {{ row.status === 1 ? '运行中' : '已停止' }}
              </span>
              <span v-else>-</span>
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
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
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
</style>
