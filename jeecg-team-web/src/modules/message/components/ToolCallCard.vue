<script setup lang="ts">
// 工具调用卡片（agent-loop 消息的组成部分）—— owner: qingyanjunzi
// 一条 agent 消息可携带多个 ToolCall，按序展示"调用→运行→结果"，可展开看参数与返回。
import { computed, ref } from 'vue';
import { PhCaretRight, PhCheck, PhX, PhCircleNotch, PhWrench } from '@phosphor-icons/vue';

export interface ToolCall {
  id: string;
  /** 工具标识（如 team_approval.create） */
  tool: string;
  /** 人话标题（如 创建审批单） */
  title: string;
  args: Record<string, string>;
  status: 'running' | 'success' | 'error';
  result?: string;
  durationMs?: number;
}

const props = defineProps<{ call: ToolCall }>();

const expanded = ref(false);

const statusIcon = computed(() => {
  if (props.call.status === 'running') return PhCircleNotch;
  if (props.call.status === 'success') return PhCheck;
  return PhX;
});

const argEntries = computed(() => Object.entries(props.call.args));
</script>

<template>
  <div class="tc" :class="`tc--${call.status}`">
    <div class="tc__head" @click="expanded = !expanded">
      <span class="tc__icon"><PhWrench :size="14" /></span>
      <span class="tc__title">{{ call.title }}</span>
      <code class="tc__tool">{{ call.tool }}</code>
      <span class="tc__status">
        <component :is="statusIcon" :size="14" :class="{ 'tc__spin': call.status === 'running' }" />
        <span v-if="call.status === 'running'">运行中</span>
        <span v-else-if="call.status === 'success'">{{ call.durationMs ? `${call.durationMs}ms` : '完成' }}</span>
        <span v-else>失败</span>
      </span>
      <PhCaretRight :size="12" class="tc__caret" :class="{ 'tc__caret--open': expanded }" />
    </div>
    <div v-if="expanded" class="tc__body">
      <div class="tc__section">
        <div class="tc__label">参数</div>
        <div v-for="[k, v] in argEntries" :key="k" class="tc__kv"><code>{{ k }}</code><span>{{ v }}</span></div>
        <div v-if="!argEntries.length" class="tc__empty">（无参数）</div>
      </div>
      <div v-if="call.result" class="tc__section">
        <div class="tc__label">返回</div>
        <pre class="tc__result">{{ call.result }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tc {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  margin-bottom: 8px;
  overflow: hidden;
}
.tc__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: var(--font-size-md);
}
.tc__head:hover {
  background: var(--border-color-light);
}
.tc__icon {
  display: inline-flex;
  color: var(--primary);
}
.tc__title {
  font-weight: 500;
  color: var(--text-main);
}
.tc__tool {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  background: var(--bg-body);
  padding: 1px 6px;
  border-radius: 4px;
}
.tc__status {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
}
.tc--success .tc__status {
  color: var(--success);
}
.tc--error .tc__status {
  color: var(--danger);
}
.tc--running .tc__status {
  color: var(--primary);
}
.tc__spin {
  animation: tcSpin 1s linear infinite;
}
@keyframes tcSpin {
  to {
    transform: rotate(360deg);
  }
}
.tc__caret {
  color: var(--text-muted);
  transition: transform 0.2s;
}
.tc__caret--open {
  transform: rotate(90deg);
}
.tc__body {
  border-top: 1px solid var(--border-color-light);
  padding: 10px 12px;
}
.tc__section + .tc__section {
  margin-top: 10px;
}
.tc__label {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-bottom: 6px;
}
.tc__kv {
  display: flex;
  gap: 10px;
  font-size: var(--font-size-sm);
  padding: 3px 0;
}
.tc__kv code {
  color: var(--primary);
  min-width: 90px;
}
.tc__empty {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.tc__result {
  font-size: var(--font-size-xs);
  background: var(--bg-body);
  border-radius: var(--radius-sm);
  padding: 8px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
