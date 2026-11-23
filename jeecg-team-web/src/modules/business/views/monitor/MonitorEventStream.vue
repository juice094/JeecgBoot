<script setup lang="ts">
// 业务事件流（监控组）—— SSE 实时消费 /team/event/stream（权限 team:task:list）
// 左 pane：事件流（自动滚动 + 暂停开关 + bizType 过滤 chips，切换即重连）
// 右 pane：本页已收事件的客户端聚合统计（按 bizType / action 计数）+ 连接状态点
// 断线：EventSource onerror 后主动关闭并退避重连（3s 起步、15s 封顶），连接态如实展示
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { buildEventStreamUrl, type StreamEvent } from '@/api/monitor';
import { EVENT_ACTION_META, EVENT_ACTION_FALLBACK_COLOR } from '@/constants/taskEventAction';

/* ---------------- 过滤与连接参数 ---------------- */
/* 后端 TeamEventVO.bizType 实际取值小写（task/approval/bug），chips 与过滤参数必须同口径 */
const BIZ_TYPE_OPTIONS = [
  { key: 'task', label: '任务' },
  { key: 'bug', label: '缺陷' },
  { key: 'approval', label: '审批' },
] as const;

const activeBizTypes = ref<string[]>([]);
const tail = 50; // 契约默认 50；后端回放后转实时推送

const events = ref<StreamEvent[]>([]);
const connected = ref(false);
const pauseScroll = ref(false);
const streamList = ref<HTMLElement | null>(null);

/* ponytail: 事件只增不清会无界增长，先硬上限 500；升级路径=环形缓冲/虚拟列表 */
const EVENTS_HARD_CAP = 500;
const RECONNECT_BASE_MS = 3000;
const RECONNECT_MAX_MS = 15000;

let source: EventSource | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let retryCount = 0;

function bizTypeParam(): string | undefined {
  return activeBizTypes.value.length ? activeBizTypes.value.join(',') : undefined;
}

function clearReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function connect() {
  disconnect();
  source = new EventSource(buildEventStreamUrl({ bizType: bizTypeParam(), tail }));
  source.onopen = () => {
    retryCount = 0;
    connected.value = true;
  };
  source.addEventListener('audit', (msg) => {
    // 后端 SseEmitter.event().name("audit") 具名事件；心跳为注释帧，不会进本回调
    try {
      const ev = JSON.parse((msg as MessageEvent).data) as StreamEvent;
      if (!ev || !ev.id) return;
      events.value.push(ev);
      if (events.value.length > EVENTS_HARD_CAP) {
        events.value.splice(0, events.value.length - EVENTS_HARD_CAP);
      }
    } catch {
      // 非 JSON 帧，忽略
    }
  });
  source.onerror = () => {
    connected.value = false;
    disconnect();
    const delay = Math.min(RECONNECT_BASE_MS * 2 ** retryCount, RECONNECT_MAX_MS);
    retryCount += 1;
    reconnectTimer = setTimeout(connect, delay);
  };
}

function disconnect() {
  if (source) {
    source.close();
    source = null;
  }
}

function toggleBizType(key: string) {
  const idx = activeBizTypes.value.indexOf(key);
  if (idx >= 0) activeBizTypes.value.splice(idx, 1);
  else activeBizTypes.value.push(key);
  reconnectNow();
}

function selectAllBizTypes() {
  if (!activeBizTypes.value.length) return;
  activeBizTypes.value = [];
  reconnectNow();
}

/** 切换过滤即重连：清零退避并立即建立新连接 */
function reconnectNow() {
  retryCount = 0;
  clearReconnect();
  connect();
}

/* ---------------- 自动滚动 ---------------- */
watch(
  () => events.value.length,
  async () => {
    if (pauseScroll.value) return;
    await nextTick();
    const el = streamList.value;
    if (el) el.scrollTop = el.scrollHeight;
  },
);

/* ---------------- 渲染辅助 ---------------- */
function actionMeta(action: string) {
  return EVENT_ACTION_META[action] ?? null;
}
function actionText(action: string): string {
  return actionMeta(action)?.text ?? action;
}
function actionColor(action: string): string {
  return actionMeta(action)?.color ?? EVENT_ACTION_FALLBACK_COLOR;
}
function bizTypeLabel(key: string): string {
  return BIZ_TYPE_OPTIONS.find((o) => o.key === key)?.label ?? key;
}
/** createTime 后端为 Date 序列化（FastJSON 可能是毫秒数或 'yyyy-MM-dd HH:mm:ss'），两种都收 */
function formatTime(iso?: string | number): string {
  if (iso == null || iso === '') return '--:--:--';
  const d = typeof iso === 'number' ? new Date(iso) : new Date(String(iso).replace(' ', 'T'));
  if (Number.isNaN(d.getTime())) return String(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

/* ---------------- 右 pane 聚合（纯本页数据） ---------------- */
const totalCount = computed(() => events.value.length);

const statsByBizType = computed(() => {
  const map = new Map<string, number>();
  for (const ev of events.value) map.set(ev.bizType, (map.get(ev.bizType) ?? 0) + 1);
  return [...map.entries()].sort((a, b) => b[1] - a[1]);
});

const statsByAction = computed(() => {
  const map = new Map<string, number>();
  for (const ev of events.value) map.set(ev.action, (map.get(ev.action) ?? 0) + 1);
  const max = Math.max(1, ...map.values());
  return [...map.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([action, count]) => ({
      action,
      count,
      text: actionText(action),
      color: actionColor(action),
      widthPct: Math.round((count / max) * 100),
    }));
});

/* ---------------- 生命周期 ---------------- */
onMounted(connect);
onBeforeUnmount(() => {
  clearReconnect();
  disconnect();
});
</script>

<template>
  <div class="card monitor-events">
    <div class="card-header monitor-events__header">
      <span class="card-title">业务事件流</span>
      <span class="conn" :class="connected ? 'conn--on' : 'conn--off'">
        <span class="conn__dot" />{{ connected ? '流中' : '已断开' }}
      </span>
    </div>

    <div class="monitor-events__body">
      <!-- 左 pane：事件流 -->
      <section class="stream-pane">
        <div class="filter-bar">
          <button
            class="chip"
            :class="{ 'chip--active': !activeBizTypes.length }"
            @click="selectAllBizTypes"
          >全部</button>
          <button
            v-for="opt in BIZ_TYPE_OPTIONS"
            :key="opt.key"
            class="chip"
            :class="{ 'chip--active': activeBizTypes.includes(opt.key) }"
            @click="toggleBizType(opt.key)"
          >{{ opt.label }}</button>
          <label class="stream-pane__pause">
            <input v-model="pauseScroll" type="checkbox" /> 暂停滚动
          </label>
        </div>

        <div ref="streamList" class="stream-list">
          <p v-if="!events.length" class="stream-empty">
            {{ connected ? '等待事件…（连接已建立，暂无回放或实时事件）' : '连接中…' }}
          </p>
          <div v-for="ev in events" v-else :key="ev.id" class="stream-row">
            <span class="stream-row__time">{{ formatTime(ev.createTime) }}</span>
            <span class="stream-row__biz tag--neutral">{{ bizTypeLabel(ev.bizType) }}</span>
            <span class="stream-row__actor">{{ ev.createBy || '-' }}</span>
            <span class="stream-row__action" :style="{ color: actionColor(ev.action) }">
              {{ actionText(ev.action) }}
            </span>
            <span v-if="ev.bizCode" class="stream-row__code">{{ ev.bizCode }}</span>
            <span v-if="ev.fromValue || ev.toValue" class="stream-row__change">
              <template v-if="ev.fromValue">{{ ev.fromValue }}</template>
              <template v-if="ev.fromValue && ev.toValue"> → </template>
              <template v-if="ev.toValue">{{ ev.toValue }}</template>
            </span>
          </div>
        </div>
      </section>

      <!-- 右 pane：快照统计 -->
      <aside class="stats-pane">
        <div class="stats-block">
          <p class="stats-block__title">连接状态</p>
          <p class="conn" :class="connected ? 'conn--on' : 'conn--off'">
            <span class="conn__dot" />{{ connected ? '流中' : '已断开' }}
          </p>
          <p class="stats-block__hint">本页已收事件 {{ totalCount }} 条</p>
        </div>

        <div class="stats-block">
          <p class="stats-block__title">按业务类型</p>
          <p v-if="!statsByBizType.length" class="stats-block__hint">暂无数据</p>
          <div v-for="[bizType, count] in statsByBizType" v-else :key="bizType" class="stat-line">
            <span class="stat-line__label">{{ bizTypeLabel(bizType) }}</span>
            <span class="stat-line__value">{{ count }}</span>
          </div>
        </div>

        <div class="stats-block">
          <p class="stats-block__title">按动作</p>
          <p v-if="!statsByAction.length" class="stats-block__hint">暂无数据</p>
          <div v-for="item in statsByAction" v-else :key="item.action" class="stat-bar">
            <div class="stat-bar__head">
              <span class="stat-bar__label" :style="{ color: item.color }">{{ item.text }}</span>
              <span class="stat-bar__value">{{ item.count }}</span>
            </div>
            <div class="stat-bar__track">
              <div class="stat-bar__fill" :style="{ width: item.widthPct + '%', background: item.color }" />
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.monitor-events {
  max-width: 1280px;
  margin: 0 auto;
}
.monitor-events__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.monitor-events__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
}
@media (max-width: 900px) {
  .monitor-events__body {
    grid-template-columns: 1fr;
  }
}

/* 连接状态点：绿=流中 / 灰=断开 */
.conn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}
.conn__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gray-500);
}
.conn--on .conn__dot {
  background: var(--success);
}

/* 过滤 chips */
.chip {
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-regular);
  border-radius: 999px;
  padding: 3px 12px;
  font-size: 13px;
  cursor: pointer;
}
.chip--active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

/* 左 pane */
.stream-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.stream-pane__pause {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  user-select: none;
}
.stream-list {
  margin-top: 8px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-body, transparent);
  height: 480px;
  overflow-y: auto;
  padding: 8px 12px;
  font-size: 13px;
}
.stream-empty {
  color: var(--text-muted);
  text-align: center;
  margin-top: 40px;
}
.stream-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid var(--border-color-light);
  flex-wrap: wrap;
}
.stream-row__time {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
  flex: 0 0 auto;
}
.stream-row__biz {
  flex: 0 0 auto;
}
.stream-row__actor {
  font-weight: 600;
  color: var(--text-regular);
  flex: 0 0 auto;
}
.stream-row__action {
  flex: 0 0 auto;
}
.stream-row__code {
  color: var(--text-muted);
  font-family: monospace;
  font-size: 12px;
  flex: 0 0 auto;
}
.stream-row__change {
  color: var(--text-muted);
  flex: 1 1 auto;
  min-width: 120px;
}

/* 右 pane */
.stats-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.stats-block {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px;
}
.stats-block__title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-regular);
}
.stats-block__hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}
.stat-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 3px 0;
}
.stat-line__label {
  color: var(--text-regular);
}
.stat-line__value {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.stat-bar {
  padding: 4px 0;
}
.stat-bar__head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 3px;
}
.stat-bar__value {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
.stat-bar__track {
  height: 6px;
  border-radius: 3px;
  background: var(--gray-100);
  overflow: hidden;
}
.stat-bar__fill {
  height: 100%;
  border-radius: 3px;
}
</style>
