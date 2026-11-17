<script setup lang="ts">
// 在线用户（监控组 · A 档首批）—— owner: showcase-dev
// 数据：jeecg 原生 GET /sys/online/list（只读口径；forceLogout 强退属写操作，本轮不开放，docs/24 备注）
import { onMounted, reactive, ref } from 'vue';
import { listOnlineUsers, type OnlineUserRow } from '@/api/monitor';

const PAGE_SIZE = 10;
const filter = reactive({ username: '' });
const pageNo = ref(1);
const total = ref(0);
const rows = ref<OnlineUserRow[]>([]);
const loading = ref(false);
const errorMsg = ref('');

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await listOnlineUsers({
      pageNo: pageNo.value,
      pageSize: PAGE_SIZE,
      username: filter.username || undefined,
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
const sexTitle = (sex?: number) => (sex === 1 ? '男' : sex === 2 ? '女' : '-');

onMounted(load);
</script>

<template>
  <div class="card" style="max-width: 900px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">在线用户</span>
      <span v-if="errorMsg" style="color: var(--danger); font-size: 13px; float: right">{{ errorMsg }}</span>
    </div>
    <div class="filter-bar">
      <input v-model="filter.username" class="form-control" placeholder="按用户名过滤" @keyup.enter="search" />
      <button class="btn btn-primary" :disabled="loading" @click="search">查询</button>
      <span class="online-total">当前在线 {{ total }} 人</span>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>用户名</th><th>姓名</th><th>性别</th><th>手机号</th><th>会话ID</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td colspan="5" style="text-align: center; color: var(--text-muted)">暂无在线用户</td>
          </tr>
          <tr v-for="row in rows" v-else :key="row.id">
            <td>{{ row.username || '-' }}</td>
            <td>{{ row.realname || '-' }}</td>
            <td>{{ sexTitle(row.sex) }}</td>
            <td>{{ row.phone || '-' }}</td>
            <td style="max-width: 280px; overflow: hidden; text-overflow: ellipsis">{{ row.id }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <button class="btn btn--sm" :disabled="pageNo <= 1 || loading" @click="go(-1)">上一页</button>
      <span class="pager-info">第 {{ pageNo }} / {{ totalPages() }} 页</span>
      <button class="btn btn--sm" :disabled="pageNo >= totalPages() || loading" @click="go(1)">下一页</button>
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
  max-width: 220px;
}
.online-total {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  margin-left: auto;
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
