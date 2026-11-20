<script setup lang="ts">
// 数据变更日志（监控组）——确权审计的数据侧：记录谁在什么时候改了哪条数据（data_table+data_id 快照，多版本）
// 数据：jeecg 原生 /sys/dataLog/list | queryDataVerList | queryCompareList（权限码 system:dataLog:*）
// 注意：sys_data_log 依赖业务侧 @AutoLog 数据日志打点才有数据，新部署下常为空表——页面如实展示"暂无数据"
import { onMounted, reactive, ref } from 'vue';
import {
  listDataLogs, listDataLogVersions, compareDataLogs, type DataLogRow,
} from '@/api/monitor';

const PAGE_SIZE = 10;
const filter = reactive({ dataTable: '', createBy: '' });
const pageNo = ref(1);
const total = ref(0);
const rows = ref<DataLogRow[]>([]);
const loading = ref(false);
const errorMsg = ref('');

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await listDataLogs({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      dataTable: filter.dataTable || undefined,
      createBy: filter.createBy || undefined,
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

/* ---------------- 历史版本 ---------------- */
const verModalOpen = ref(false);
const verLoading = ref(false);
const verRows = ref<DataLogRow[]>([]);
const verTarget = ref('');

async function openVersions(row: DataLogRow) {
  verModalOpen.value = true;
  verLoading.value = true;
  verRows.value = [];
  verTarget.value = `${row.dataTable} / ${row.dataId}`;
  try {
    const data = await listDataLogVersions({ dataTable: row.dataTable ?? '', dataId: row.dataId ?? '' });
    verRows.value = (data.result ?? []).sort((a, b) => (a.dataVersion ?? 0) - (b.dataVersion ?? 0));
  } catch (e: any) {
    verRows.value = [];
    errorMsg.value = e.message || '加载历史版本失败';
  } finally {
    verLoading.value = false;
  }
}

/* ---------------- 两版本对比 ---------------- */
const cmpModalOpen = ref(false);
const cmpLoading = ref(false);
const cmpRows = ref<DataLogRow[]>([]);
const cmpIds = ref('');

async function openCompare(a: DataLogRow, b: DataLogRow) {
  cmpModalOpen.value = true;
  cmpLoading.value = true;
  cmpRows.value = [];
  cmpIds.value = `v${a.dataVersion} ↔ v${b.dataVersion}`;
  try {
    const data = await compareDataLogs(a.id, b.id);
    cmpRows.value = (data.result ?? []).sort((x, y) => (x.dataVersion ?? 0) - (y.dataVersion ?? 0));
  } catch (e: any) {
    cmpRows.value = [];
    errorMsg.value = e.message || '加载对比失败';
  } finally {
    cmpLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="card" style="max-width: 1100px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">数据变更日志</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px; float: right">{{ errorMsg }}</span>
    </div>
    <div class="filter-bar">
      <input v-model="filter.dataTable" class="form-control" placeholder="按表名过滤（如 sys_user）" @keyup.enter="search" />
      <input v-model="filter.createBy" class="form-control" placeholder="按操作人过滤" @keyup.enter="search" />
      <button class="btn btn-primary" :disabled="loading" @click="search">查询</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>数据表</th><th>数据ID</th><th>版本</th><th>操作人</th><th>变更时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="6" style="text-align: center; color: var(--text-muted)">
              暂无数据。数据日志依赖业务接口的数据日志打点（@AutoLog 数据日志）才会产生记录。
            </td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td><code>{{ row.dataTable || '-' }}</code></td>
            <td style="max-width: 200px; overflow: hidden; text-overflow: ellipsis">{{ row.dataId || '-' }}</td>
            <td>v{{ row.dataVersion ?? '-' }}</td>
            <td>{{ row.createName || row.createBy || '-' }}</td>
            <td>{{ row.createTime || '-' }}</td>
            <td>
              <button class="btn btn--xs" @click="openVersions(row)">历史版本</button>
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

    <!-- 历史版本弹窗 -->
    <div v-if="verModalOpen" class="modal-mask" @click.self="verModalOpen = false">
      <div class="modal-card">
        <div class="card-header" style="margin-bottom: 12px">
          <span class="card-title">历史版本 · {{ verTarget }}</span>
          <button class="btn btn--xs" style="float: right" @click="verModalOpen = false">关闭</button>
        </div>
        <p v-if="verLoading" style="color: var(--text-muted); font-size: 13px">加载中…</p>
        <p v-else-if="!verRows.length" style="color: var(--text-muted); font-size: 13px">该数据暂无历史版本。</p>
        <table v-else class="data-table">
          <thead>
            <tr><th>版本</th><th>操作人</th><th>时间</th><th>内容快照</th><th>对比</th></tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in verRows" :key="row.id">
              <td>v{{ row.dataVersion ?? '-' }}</td>
              <td>{{ row.createName || row.createBy || '-' }}</td>
              <td>{{ row.createTime || '-' }}</td>
              <td style="max-width: 320px"><pre class="snapshot">{{ row.dataContent || '-' }}</pre></td>
              <td>
                <button
                  v-if="idx > 0"
                  class="btn btn--xs"
                  @click="openCompare(verRows[idx - 1], row)"
                >与上一版对比</button>
                <span v-else style="color: var(--text-muted); font-size: 12px">基准版本</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 版本对比弹窗 -->
    <div v-if="cmpModalOpen" class="modal-mask" @click.self="cmpModalOpen = false">
      <div class="modal-card modal-card--wide">
        <div class="card-header" style="margin-bottom: 12px">
          <span class="card-title">版本对比 · {{ cmpIds }}</span>
          <button class="btn btn--xs" style="float: right" @click="cmpModalOpen = false">关闭</button>
        </div>
        <p v-if="cmpLoading" style="color: var(--text-muted); font-size: 13px">加载中…</p>
        <div v-else class="compare-grid">
          <div v-for="row in cmpRows" :key="row.id" class="compare-col">
            <p class="compare-col__title">v{{ row.dataVersion }} · {{ row.createTime || '-' }}</p>
            <pre class="snapshot">{{ row.dataContent || '-' }}</pre>
          </div>
        </div>
      </div>
    </div>
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
  max-width: 240px;
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
.snapshot {
  margin: 0;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-regular);
}
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: 20px;
  width: min(720px, calc(100vw - 48px));
  max-height: 80vh;
  overflow: auto;
}
.modal-card--wide {
  width: min(960px, calc(100vw - 48px));
}
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.compare-col__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-regular);
  margin: 0 0 8px;
}
@media (max-width: 720px) {
  .compare-grid {
    grid-template-columns: 1fr;
  }
}
</style>
