/* 事件动作 → 文案 / 图标 / 动作着色 —— TaskDetail 时间线与 MonitorEventStream 事件流共用
 * （2026-11-23 自 TaskDetail.vue 抽出；对齐 GitHub 时间线「动作着色」呈现） */
import type { Component } from 'vue';
import {
  PhArrowCounterClockwise, PhArrowsLeftRight, PhCheckCircle, PhPlusCircle, PhXCircle,
} from '@phosphor-icons/vue';

export const EVENT_ACTION_META: Record<string, { text: string; color: string; icon: Component }> = {
  create: { text: '创建了此任务', color: 'var(--success)', icon: PhPlusCircle },
  submit: { text: '提交了任务', color: 'var(--info)', icon: PhArrowCounterClockwise },
  change_status: { text: '变更了状态', color: 'var(--primary)', icon: PhArrowsLeftRight },
  approve: { text: '通过了审批', color: 'var(--success)', icon: PhCheckCircle },
  reject: { text: '拒绝了审批', color: 'var(--danger)', icon: PhXCircle },
  return: { text: '退回了任务', color: 'var(--warning)', icon: PhArrowCounterClockwise },
  cancel: { text: '撤销了任务', color: 'var(--gray-500)', icon: PhXCircle },
  resubmit: { text: '重提了任务', color: 'var(--info)', icon: PhArrowCounterClockwise },
};

/** 未知动作的兜底色（与时间线口径一致） */
export const EVENT_ACTION_FALLBACK_COLOR = 'var(--text-muted)';
