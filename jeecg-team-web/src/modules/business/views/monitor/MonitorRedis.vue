<script setup lang="ts">
// Redis 监控（监控组 · A 档首批）—— owner: showcase-dev
// 数据：jeecg 原生 /sys/actuator/redis/{keysSize,memoryInfo,info}（只读；docs/24 §三 B 档——旧页图表重置为本页卡片）
// 口径注意：keysSize/memoryInfo 为裸 Map 返回（非 Result 包装）；三个端点均 @RequiresRoles("admin")
import { computed, onMounted, ref } from 'vue';
import { fetchRedisInfo, fetchRedisKeysSize, fetchRedisMemoryInfo, type RedisInfoItem } from '@/api/monitor';

const loading = ref(false);
const errorMsg = ref('');
const dbSize = ref<number | null>(null);
const usedMemory = ref<number | null>(null);
const infoRows = ref<RedisInfoItem[]>([]);
const keyword = ref('');

const filteredInfo = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return infoRows.value;
  return infoRows.value.filter(
    (r) => (r.key || '').toLowerCase().includes(kw) || (r.description || '').toLowerCase().includes(kw),
  );
});

function humanBytes(v: number | null): string {
  if (v == null || Number.isNaN(v)) return '-';
  if (v >= 1073741824) return (v / 1073741824).toFixed(2) + ' GB';
  if (v >= 1048576) return (v / 1048576).toFixed(1) + ' MB';
  if (v >= 1024) return (v / 1024).toFixed(1) + ' KB';
  return v + ' B';
}

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const [keys, memory, info] = await Promise.all([
      fetchRedisKeysSize(),
      fetchRedisMemoryInfo(),
      fetchRedisInfo(),
    ]);
    dbSize.value = keys?.dbSize ?? null;
    usedMemory.value = memory?.used_memory != null ? Number(memory.used_memory) : null;
    infoRows.value = info?.result ?? [];
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败（Redis 监控需 admin 角色）';
    dbSize.value = null;
    usedMemory.value = null;
    infoRows.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="card" style="max-width: 1100px; margin: 0 auto">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">Redis 监控</span>
      <button class="btn btn--sm" style="float: right" :disabled="loading" @click="load">刷新</button>
    </div>
    <p v-if="errorMsg" style="color: var(--danger); font-size: 13px">{{ errorMsg }}</p>
    <div class="metric-cards">
      <div class="metric-card">
        <div class="metric-value">{{ loading ? '…' : (dbSize ?? '-') }}</div>
        <div class="metric-label">Key 数量</div>
      </div>
      <div class="metric-card">
        <div class="metric-value">{{ loading ? '…' : humanBytes(usedMemory) }}</div>
        <div class="metric-label">占用内存</div>
      </div>
    </div>
    <div class="filter-bar">
      <input v-model="keyword" class="form-control" placeholder="按关键字过滤信息项（如 memory、version）" />
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>配置项</th><th>值</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="3" style="text-align: center; color: var(--text-muted)">加载中…</td>
          </tr>
          <tr v-else-if="!filteredInfo.length">
            <td colspan="3" style="text-align: center; color: var(--text-muted)">{{ errorMsg ? '无法加载' : '暂无匹配项' }}</td>
          </tr>
          <tr v-for="(row, idx) in filteredInfo" v-else :key="row.key || idx">
            <td style="white-space: nowrap"><code>{{ row.key || '-' }}</code></td>
            <td style="max-width: 360px; word-break: break-all">{{ row.value || '-' }}</td>
            <td style="max-width: 280px">{{ row.description || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.metric-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.metric-card {
  background: var(--bg-body);
  border-radius: var(--radius-md);
  padding: 18px;
  text-align: center;
}
.metric-value {
  font-size: 26px;
  font-weight: 600;
  color: var(--primary);
}
.metric-label {
  margin-top: 6px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.filter-bar {
  margin-bottom: 12px;
}
.filter-bar .form-control {
  max-width: 360px;
}
.table-wrap {
  margin-top: 8px;
}
</style>
