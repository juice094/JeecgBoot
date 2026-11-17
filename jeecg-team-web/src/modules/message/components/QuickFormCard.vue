<script setup lang="ts">
// 会话内快捷表单卡 —— owner: qingyanjunzi
// /apply、/bug 命令唤起：不离开聊天完成工具工作台的核心表单填写。
// 提交走对应后端 API（/team/approval/submit、/team/bug/add），成功后由父组件渲染为 tool-call 消息。
import { reactive, ref } from 'vue';
import { PhNotePencil, PhBug } from '@phosphor-icons/vue';
import http from '@/utils/http';

const props = defineProps<{ form: 'approval' | 'bug' }>();
const emit = defineEmits<{ (e: 'submit', payload: { title: string; summary: string }): void }>();

const isApproval = props.form === 'approval';

const form = reactive(
  isApproval
    ? { category: '加班申请', title: '', reason: '', startDate: '', endDate: '' }
    : { description: '' }
);
const submitting = ref(false);
const errorMsg = ref('');

async function submit() {
  errorMsg.value = '';
  if (isApproval) {
    const f = form as { category: string; title: string; reason: string; startDate: string; endDate: string };
    if (!f.title.trim()) {
      errorMsg.value = '请填写事项标题';
      return;
    }
    submitting.value = true;
    try {
      await http.post('/team/approval/submit', {
        approval: { category: f.category, title: f.title, reason: f.reason, startDate: f.startDate || undefined, endDate: f.endDate || undefined },
        steps: [
          { approverName: '部门主管', approverRole: '默认一级审批' },
          { approverName: '分管副总', approverRole: '默认二级审批' },
        ],
      });
      emit('submit', { title: f.title, summary: `已提交${f.category}` });
    } catch (e: any) {
      errorMsg.value = e.message || '提交失败';
    } finally {
      submitting.value = false;
    }
  } else {
    const f = form as { description: string };
    if (!f.description.trim()) {
      errorMsg.value = '请填写问题描述';
      return;
    }
    submitting.value = true;
    try {
      await http.post('/team/bug/add', { description: f.description });
      emit('submit', { title: 'Bug 反馈', summary: '已提交' });
    } catch (e: any) {
      errorMsg.value = e.message || '提交失败';
    } finally {
      submitting.value = false;
    }
  }
}
</script>

<template>
  <div class="qf">
    <div class="qf__head">
      <component :is="isApproval ? PhNotePencil : PhBug" :size="15" />
      <span>{{ isApproval ? '快捷申请' : '快捷 Bug 反馈' }}</span>
      <span class="qf__hint">{{ isApproval ? '提交后进入审批流' : '提交后进入 open 状态' }}</span>
    </div>
    <template v-if="isApproval">
      <div class="qf__row">
        <select v-model="(form as any).category" class="qf__input qf__select">
          <option>加班申请</option>
          <option>请假申请</option>
          <option>出差申请</option>
          <option>费用报销</option>
          <option>采购申请</option>
        </select>
        <input v-model="(form as any).title" class="qf__input" placeholder="事项标题 *" />
      </div>
      <textarea v-model="(form as any).reason" class="qf__input qf__textarea" placeholder="申请事由"></textarea>
      <div class="qf__row">
        <input v-model="(form as any).startDate" type="date" class="qf__input" />
        <input v-model="(form as any).endDate" type="date" class="qf__input" />
      </div>
    </template>
    <textarea v-else v-model="(form as any).description" class="qf__input qf__textarea" placeholder="问题描述 *"></textarea>
    <div class="qf__foot">
      <span v-if="errorMsg" class="qf__error">{{ errorMsg }}</span>
      <button class="qf__submit" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '提交' }}</button>
    </div>
  </div>
</template>

<style scoped>
.qf {
  border: 1px solid var(--primary);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  padding: 12px;
  margin-bottom: 8px;
}
.qf__head {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  font-size: var(--font-size-md);
  font-weight: 500;
  margin-bottom: 10px;
}
.qf__hint {
  margin-left: auto;
  font-weight: normal;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}
.qf__row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.qf__input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  outline: none;
  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.qf__input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 12%, transparent);
}
.qf__select {
  flex: 0 0 130px;
}
.qf__textarea {
  width: 100%;
  min-height: 60px;
  resize: vertical;
  margin-bottom: 8px;
  box-sizing: border-box;
}
.qf__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
.qf__error {
  color: var(--danger);
  font-size: var(--font-size-sm);
}
.qf__submit {
  border: none;
  background: var(--primary);
  color: #fff;
  padding: 7px 20px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.qf__submit:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
