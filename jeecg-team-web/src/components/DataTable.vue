<script setup lang="ts">
// 通用表格（列配置驱动 + 分页）—— 管理域 P0-P2 复用。
// 设计：列定义只描述标题/字段/宽度/对齐；复杂单元格用具名 slot `#cell-<dataIndex>`，
// 避免 v-html 注入风险。分页由父组件持有 pageNo/pageSize，通过 v-model 双向绑定。
import { computed } from 'vue';
import { PhCaretLeft, PhCaretRight } from '@phosphor-icons/vue';

export interface DataTableColumn {
  title: string;
  dataIndex: string;
  width?: string | number;
  align?: 'left' | 'center' | 'right';
}

const props = withDefaults(
  defineProps<{
    columns: DataTableColumn[];
    rows: Record<string, unknown>[];
    rowKey?: string;
    total?: number;
    pageNo?: number;
    pageSize?: number;
    loading?: boolean;
    errorMsg?: string;
    emptyText?: string;
  }>(),
  {
    rowKey: 'id',
    total: 0,
    pageNo: 1,
    pageSize: 20,
    loading: false,
    errorMsg: '',
    emptyText: '暂无数据',
  }
);

const emit = defineEmits<{
  (e: 'update:pageNo', v: number): void;
  (e: 'update:pageSize', v: number): void;
}>();

const totalPages = computed(() =>
  Math.max(1, Math.ceil((props.total || 0) / (props.pageSize || 1)))
);

const pageButtons = computed(() => {
  const tp = totalPages.value;
  const cp = props.pageNo;
  const set = new Set<number>([1, tp, cp - 1, cp, cp + 1]);
  return [...set].filter((p) => p >= 1 && p <= tp).sort((a, b) => a - b);
});

function goPage(p: number) {
  if (p < 1 || p > totalPages.value || p === props.pageNo) return;
  emit('update:pageNo', p);
}
</script>

<template>
  <div class="dt">
    <div class="dt__wrap">
      <table class="dt__table">
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.dataIndex"
              :style="{ width: typeof col.width === 'number' ? col.width + 'px' : col.width, textAlign: col.align || 'left' }"
            >
              {{ col.title }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td :colspan="columns.length" class="dt__tip">加载中…</td>
          </tr>
          <tr v-else-if="errorMsg">
            <td :colspan="columns.length" class="dt__tip dt__tip--err">{{ errorMsg }}</td>
          </tr>
          <tr v-else-if="!rows.length">
            <td :colspan="columns.length" class="dt__tip">{{ emptyText }}</td>
          </tr>
          <template v-else>
            <tr v-for="(row, idx) in rows" :key="String(row[rowKey] ?? idx)">
              <td
                v-for="col in columns"
                :key="col.dataIndex"
                :style="{ textAlign: col.align || 'left' }"
              >
                <slot :name="`cell-${col.dataIndex}`" :row="row" :value="row[col.dataIndex]" :index="idx">
                  {{ row[col.dataIndex] ?? '-' }}
                </slot>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div v-if="total > 0" class="dt__pager">
      <span class="dt__total">共 {{ total }} 条 · 第 {{ pageNo }}/{{ totalPages }} 页</span>
      <div class="dt__pages">
        <button class="dt__page-btn" :disabled="pageNo <= 1" title="上一页" @click="goPage(pageNo - 1)">
          <PhCaretLeft :size="14" />
        </button>
        <template v-for="(p, i) in pageButtons" :key="p">
          <span v-if="i > 0 && p - pageButtons[i - 1] > 1" class="dt__ellipsis">…</span>
          <button
            class="dt__page-btn"
            :class="{ 'dt__page-btn--active': p === pageNo }"
            @click="goPage(p)"
          >
            {{ p }}
          </button>
        </template>
        <button class="dt__page-btn" :disabled="pageNo >= totalPages" title="下一页" @click="goPage(pageNo + 1)">
          <PhCaretRight :size="14" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dt {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dt__wrap {
  overflow-x: auto;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
.dt__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.dt__table th {
  background: var(--bg-body);
  color: var(--text-muted);
  font-weight: 500;
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
  white-space: nowrap;
}
.dt__table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color-light, var(--border-color));
  color: var(--text-main);
  vertical-align: middle;
}
.dt__table tbody tr:last-child td {
  border-bottom: none;
}
.dt__table tbody tr:hover td {
  background: var(--gray-50, transparent);
}
.dt__tip {
  text-align: center;
  color: var(--text-muted);
  padding: 24px 12px;
}
.dt__tip--err {
  color: var(--danger);
}
.dt__pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--text-muted);
}
.dt__pages {
  display: flex;
  align-items: center;
  gap: 4px;
}
.dt__page-btn {
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-regular);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.dt__page-btn:hover:not(:disabled) {
  color: var(--primary);
  border-color: var(--primary);
}
.dt__page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.dt__page-btn--active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}
.dt__ellipsis {
  padding: 0 2px;
}
</style>
