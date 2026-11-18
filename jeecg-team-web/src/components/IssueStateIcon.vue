<script setup lang="ts">
// 列表页状态符号（无底色图标，对齐 GitHub 列表行 status 区）
// 与详情页的实心胶囊 StatusBadge 是两种组件，不要混用。
import { PhCheckCircle, PhCircle, PhCircleHalfTilt, PhXCircle } from '@phosphor-icons/vue';
import type { UnifiedState } from '@/constants/issue';

const props = defineProps<{ state: UnifiedState }>();

const META: Record<UnifiedState, { icon: unknown; color: string }> = {
  open: { icon: PhCircle, color: 'var(--success)' },
  active: { icon: PhCircleHalfTilt, color: 'var(--warning)' },
  done: { icon: PhCheckCircle, color: 'var(--primary)' },
  closed: { icon: PhXCircle, color: 'var(--gray-500)' },
};
</script>

<template>
  <component :is="META[props.state].icon" :size="16" :color="META[props.state].color" aria-hidden="true" />
</template>
