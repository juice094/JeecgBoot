import { defineStore } from 'pinia';
import { ref } from 'vue';
import { listApprovalTodos } from '@/api/team';
import { listMailMessages, listMyMailAccounts } from '@/api/mail';

/** 轮询周期：60s（角标是弱实时语义，审批/未读数延迟一分钟可接受） */
const POLL_INTERVAL_MS = 60_000;

/**
 * 侧栏角标真实计数（原路由 meta.badge 硬编码为占位，接口就绪后替换——即本 store）。
 * 口径：
 * - approvalTodo = /team/approval/todo total（待我审批数，服务端按登录人过滤）
 * - mailUnread   = oa_mail_message_index read_flag=0 计数（只查索引表，轻量）
 * 未登录/接口失败时保持旧值不清零，避免登录抖动把角标闪没。
 */
export const useBadgeStore = defineStore('badges', () => {
  const approvalTodo = ref(0);
  const mailUnread = ref(0);
  let timer: ReturnType<typeof setInterval> | null = null;

  async function refresh() {
    try {
      const todo = await listApprovalTodos({ pageNo: 1, pageSize: 1 });
      approvalTodo.value = todo.result?.total ?? 0;
    } catch {
      // 未登录或网络失败：保持旧值
    }
    try {
      const accounts = await listMyMailAccounts();
      const account = accounts.result?.[0];
      if (!account) {
        mailUnread.value = 0;
      } else {
        const unread = await listMailMessages({
          accountId: account.id,
          unreadOnly: true,
          pageNo: 1,
          pageSize: 1,
        });
        mailUnread.value = unread.result?.total ?? 0;
      }
    } catch {
      // 未绑定邮箱/未登录：保持旧值
    }
  }

  function startPolling() {
    if (timer) return;
    void refresh();
    timer = setInterval(() => void refresh(), POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  return { approvalTodo, mailUnread, refresh, startPolling, stopPolling };
});
