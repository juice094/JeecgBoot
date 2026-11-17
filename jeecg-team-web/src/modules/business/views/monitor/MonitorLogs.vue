<script setup lang="ts">
// 日志管理（监控组 · A 档首批）—— owner: showcase-dev
// 数据：jeecg 原生 GET /sys/log/list（只读；来源 docs/24 §三 A 档，调用逻辑参考 vue3 monitor/log/log.api.ts）
import { onMounted, reactive, ref } from 'vue';
import { listLogs, type SysLogRow } from '@/api/monitor';

const PAGE_SIZE = 10;
const filter = reactive({ username: '', logType: undefined as number | undefined });
const pageNo = ref(1);
const total = ref(0);
const rows = ref<SysLogRow[]>([]);
const loading = ref(false);
const errorMsg = ref('');

const LOG_TYPE_TAG: Record<number, string> = { 1: 'tag-info', 2: 'tag-success' };
const LOG_TYPE_TITLE: Record<number, string> = { 1: '登录', 2: '操作' };

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await listLogs({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      username: filter.username || undefined,
      logType: filter.logType,
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
      <span class="card-title">日志管理</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px; float: right">{{ errorMsg }}</span>
    </div>
    <div class="filter-bar">
      <input v-model="filter.username" class="form-control" placeholder="按用户名过滤" @keyup.enter="search" />
      <select v-model="filter.logType" class="form-control" style="max-width: 140px">
        <option :value="undefined">全部类型</option>
        <option :value="1">登录日志</option>
        <option :value="2">操作日志</option>
      </select>
      <button class="btn btn-primary" :disabled="loading" @click="search">查询</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>时间</th><th>用户</th><th>类型</th><th>内容</th><th>IP</th><th>耗时</th><th>请求</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="7" style="text-align: center; color: var(--text-muted)">暂无数据</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td style="white-space: nowrap">{{ row.createTime || '-' }}</td>
            <td>{{ row.username || '-' }}</td>
            <td>
              <span v-if="row.logType != null" class="tag" :class="LOG_TYPE_TAG[row.logType]">{{ LOG_TYPE_TITLE[row.logType] || row.logType }}</span>
              <span v-else>-</span>
            </td>
            <td style="max-width: 320px">{{ row.logContent || '-' }}</td>
            <td>{{ row.ip || '-' }}</td>
            <td>{{ row.costTime != null ? row.costTime + 'ms' : '-' }}</td>
            <td style="max-width: 220px; overflow: hidden; text-overflow: ellipsis">{{ row.requestUrl || '-' }}</td>
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
  max-width: 220px;
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
