<script setup lang="ts">
// 会话工作台 —— owner: qingyanjunzi
// 设计定位（2026-11-17 宿裁定，重做）：
//   同一界面服务两类会话 ——
//   1) 人-人团队消息（气泡 + 已读，Mattermost 集成后接真实数据）
//   2) 人-Agent 多轮工具循环（agent 消息携带 toolCalls 序列，ToolCallCard 展示调用→运行→结果）
//   斜杠命令：输入 '/' 唤起命令面板 ——
//   · navigate 类：切到工具工作台并打开对应页面（路由 watcher 自动切模式）
//   · form 类：会话内快捷填表（QuickFormCard），提交直达 /team/approval、/team/bug
// 当前为示例数据；真实消息源 = Mattermost（docs/13 §一），toolCalls = Agent 执行回执。
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue';
import { useRouter } from 'vue-router';
import { PhPaperclip, PhMicrophone, PhCommand, PhRobot, PhCircleNotch } from '@phosphor-icons/vue';
import ToolCallCard, { type ToolCall } from '../components/ToolCallCard.vue';
import CommandPalette from '../components/CommandPalette.vue';
import QuickFormCard from '../components/QuickFormCard.vue';
import type { ChatCommand } from '../commands';

type Msg =
  | { id: string; kind: 'user'; text: string; time: string }
  | { id: string; kind: 'agent'; text: string; time: string; toolCalls: ToolCall[] }
  | { id: string; kind: 'system'; text: string; time: string }
  | { id: string; kind: 'formcard'; form: 'approval' | 'bug'; time: string };

interface Conversation {
  id: string;
  name: string;
  type: 'group' | 'agent' | 'single';
  lastMsg: string;
  time: string;
  unread: number;
  online?: boolean;
  messages: Msg[];
}

const conversations = ref<Conversation[]>([
  {
    id: 'agent',
    name: '效率助手',
    type: 'agent',
    lastMsg: '已为你创建加班申请，单号 OA-…',
    time: '09:58',
    unread: 0,
    messages: [
      {
        id: 'a1',
        kind: 'user',
        text: '帮我发起本周六的加班申请，事由是迎新筹备',
        time: '09:55',
      },
      {
        id: 'a2',
        kind: 'agent',
        text: '',
        time: '09:56',
        toolCalls: [
          {
            id: 'tc1',
            tool: 'sys_user.current',
            title: '获取当前用户',
            args: {},
            status: 'success',
            result: '{ "username": "admin", "dept": "总经办" }',
            durationMs: 42,
          },
          {
            id: 'tc2',
            tool: 'team_approval.submit',
            title: '创建审批单',
            args: { category: '加班申请', title: '关于迎新筹备加班申请', startDate: '2026-11-21', endDate: '2026-11-22' },
            status: 'success',
            result: '{ "code": "OA-20261117-003", "status": "pending" }',
            durationMs: 138,
          },
          {
            id: 'tc3',
            tool: 'team_approval.notify',
            title: '通知一级审批人',
            args: { approver: '部门主管' },
            status: 'running',
          },
        ],
      },
      {
        id: 'a3',
        kind: 'agent',
        text: '已为你创建加班申请（OA-20261117-003），已提交部门主管审批，进度会实时同步到这里。',
        time: '09:56',
        toolCalls: [],
      },
      { id: 'a4', kind: 'system', text: '审批进度变更：部门主管已通过，待分管副总审批', time: '09:58' },
    ],
  },
  {
    id: '1',
    name: '需求设计（群）',
    type: 'group',
    lastMsg: '表单字段我补一下',
    time: '10:24',
    unread: 2,
    messages: [
      { id: 'm1', kind: 'user', text: '表单字段我补一下，下午评审', time: '10:20' },
      { id: 'm2', kind: 'system', text: 'qingyanjunzi 邀请 Ym3699 加入群聊', time: '10:22' },
      { id: 'm3', kind: 'user', text: '收到，我先把审批流状态机整理出来', time: '10:24' },
    ],
  },
  {
    id: '3',
    name: '开发联调（群）',
    type: 'group',
    lastMsg: '[文件] 接口约定.md',
    time: '昨天',
    unread: 0,
    messages: [
      { id: 'g1', kind: 'user', text: '[文件] 接口约定.md', time: '昨天' },
      { id: 'g2', kind: 'system', text: 'juice094 更新了接口约定 v3', time: '昨天' },
    ],
  },
]);

const currentId = ref('agent');
const current = computed(() => conversations.value.find((c) => c.id === currentId.value)!);

const draft = ref('');
const paletteOpen = computed(() => draft.value.startsWith('/'));
const messagesEl = useTemplateRef<HTMLDivElement>('messagesEl');
const router = useRouter();

watch(
  () => current.value.messages.length,
  async () => {
    await nextTick();
    if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
  }
);

function now(): string {
  return new Date().toTimeString().slice(0, 5);
}

/** 发普通文本：人会话直发；Agent 会话模拟一轮"理解→调用→答复"的工具循环 */
function send() {
  const text = draft.value.trim();
  if (!text || paletteOpen.value) return;
  draft.value = '';
  current.value.messages.push({ id: `u${Date.now()}`, kind: 'user', text, time: now() });

  if (current.value.type === 'agent') {
    const callId = `tc${Date.now()}`;
    current.value.messages.push({
      id: `a${Date.now()}`,
      kind: 'agent',
      text: '',
      time: now(),
      toolCalls: [
        {
          id: callId,
          tool: 'team_approval.list',
          title: '查询相关数据',
          args: { keyword: text.slice(0, 20) },
          status: 'running',
        },
      ],
    });
    setTimeout(() => {
      const msg = current.value.messages.find((m) => m.kind === 'agent' && m.toolCalls.some((t) => t.id === callId));
      if (msg && msg.kind === 'agent') {
        const call = msg.toolCalls.find((t) => t.id === callId)!;
        call.status = 'success';
        call.durationMs = 120 + Math.floor(Math.random() * 200);
        call.result = '{ "total": 0 }';
        msg.text = '处理完成。需要我帮你跳转到对应的工作台页面吗？输入 / 可以唤起命令。';
      }
    }, 1200);
  }
}

function onComposerKeydown(event: KeyboardEvent) {
  // 面板打开时 Enter 由 CommandPalette 的全局捕获处理（选中命令），此处只处理普通发送
  if (event.key === 'Enter' && !event.shiftKey && !paletteOpen.value) {
    event.preventDefault();
    send();
  }
}

/** 命令执行：navigate 跳工具工作台；form 插入会话内快捷表单卡 */
function runCommand(cmd: ChatCommand) {
  draft.value = '';
  if (cmd.action.type === 'navigate') {
    router.push(cmd.action.path); // AppLayout watcher 自动切到工具模式
    return;
  }
  current.value.messages.push({ id: `f${Date.now()}`, kind: 'formcard', form: cmd.action.form, time: now() });
}

/** 快捷表单提交成功：卡片收敛为一条成功回执 + 系统提示 */
function onQuickFormSubmit(msgId: string, payload: { title: string; summary: string }) {
  const idx = current.value.messages.findIndex((m) => m.id === msgId);
  if (idx < 0) return;
  current.value.messages.splice(idx, 1, {
    id: `a${Date.now()}`,
    kind: 'agent',
    text: '',
    time: now(),
    toolCalls: [
      {
        id: `tc${Date.now()}`,
        tool: msgId.startsWith('fa') ? 'team_approval.submit' : 'team_bug.add',
        title: payload.title,
        args: { via: '会话工作台快捷表单' },
        status: 'success',
        result: `{ "message": "${payload.summary}" }`,
        durationMs: 160,
      },
    ],
  });
  current.value.messages.push({ id: `s${Date.now()}`, kind: 'system', text: payload.summary, time: now() });
}
</script>

<template>
  <div class="chat">
    <!-- 会话列表 -->
    <aside class="chat__list">
      <div class="chat__list-search">
        <input placeholder="搜索会话 / 成员 / Agent" />
      </div>
      <div
        v-for="c in conversations"
        :key="c.id"
        class="conv"
        :class="{ 'conv--active': c.id === currentId }"
        @click="currentId = c.id"
      >
        <div class="conv__avatar" :class="{ 'conv__avatar--agent': c.type === 'agent' }">
          <PhRobot v-if="c.type === 'agent'" :size="18" />
          <template v-else>{{ c.name[0] }}</template>
          <i v-if="c.online" class="conv__dot" />
        </div>
        <div class="conv__body">
          <div class="conv__top">
            <span class="conv__name">{{ c.name }}</span>
            <span class="conv__time">{{ c.time }}</span>
          </div>
          <div class="conv__bottom">
            <span class="conv__last">{{ c.lastMsg }}</span>
            <span v-if="c.unread" class="conv__unread">{{ c.unread }}</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 消息区 -->
    <section class="chat__main">
      <div ref="messagesEl" class="chat__messages">
        <template v-for="m in current.messages" :key="m.id">
          <!-- 系统提示：居中弱化 -->
          <div v-if="m.kind === 'system'" class="sysline">
            <span>{{ m.text }} · {{ m.time }}</span>
          </div>

          <!-- 会话内快捷表单 -->
          <div v-else-if="m.kind === 'formcard'" class="formcard-wrap">
            <QuickFormCard :form="m.form" @submit="(p) => onQuickFormSubmit(m.id, p)" />
          </div>

          <!-- agent 消息：头像 + 工具调用序列 + 文本 -->
          <div v-else-if="m.kind === 'agent'" class="msg-row msg-row--agent">
            <div class="msg-row__avatar msg-row__avatar--agent"><PhRobot :size="16" /></div>
            <div class="msg-row__content">
              <div v-for="call in m.toolCalls" :key="call.id" class="msg-row__tools">
                <ToolCallCard :call="call" />
              </div>
              <div v-if="m.text" class="bubble bubble--agent">
                <PhCircleNotch v-if="!m.text" :size="14" class="bubble__typing" />
                {{ m.text }}
              </div>
              <div class="msg-row__meta">效率助手 · {{ m.time }}</div>
            </div>
          </div>

          <!-- 用户消息 -->
          <div v-else class="msg-row" :class="{ 'msg-row--self': true }">
            <div class="msg-row__content">
              <div class="bubble">{{ m.text }}</div>
              <div class="msg-row__meta">我 · {{ m.time }}</div>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区：斜杠命令唤起工具工作台 -->
      <div class="chat__input">
        <div class="chat__toolbar">
          <button title="发送文件"><PhPaperclip :size="17" /></button>
          <button title="语音消息"><PhMicrophone :size="17" /></button>
          <button title="命令面板（/ 唤起）" @click="draft = '/'"><PhCommand :size="17" /></button>
          <span class="chat__hint">输入 <code>/</code> 唤起工具命令：申请、审批、Bug、报表…</span>
        </div>
        <div class="chat__composer">
          <CommandPalette v-if="paletteOpen" :input="draft" @pick="runCommand" @close="draft = ''" />
          <textarea
            v-model="draft"
            placeholder="输入消息，Enter 发送；输入 / 唤起工具命令"
            @keydown="onComposerKeydown"
          ></textarea>
          <button class="chat__send" :disabled="paletteOpen" @click="send">发送</button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  height: 100%;
  gap: 0;
  flex: 1;
  min-width: 0;
}

/* 会话列表 */
.chat__list {
  width: 260px;
  flex-shrink: 0;
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  overflow-y: auto;
}
.chat__list-search {
  padding: 10px;
  border-bottom: 1px solid var(--border-color-light);
}
.chat__list-search input {
  width: 100%;
  height: 32px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0 10px;
  font-size: var(--font-size-md);
  outline: none;
  box-sizing: border-box;
}
.conv {
  display: flex;
  gap: 10px;
  padding: 12px;
  cursor: pointer;
}
.conv:hover {
  background: var(--border-color-light);
}
.conv--active {
  background: var(--primary-light);
}
.conv__avatar {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.conv__avatar--agent {
  background: linear-gradient(135deg, var(--primary), #2aa198);
}
.conv__dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--success);
  border: 2px solid #fff;
}
.conv__body {
  flex: 1;
  min-width: 0;
}
.conv__top,
.conv__bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.conv__name {
  font-size: var(--font-size-base);
  font-weight: 600;
}
.conv__time {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
.conv__last {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv__unread {
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--danger);
  color: #fff;
  font-size: var(--font-size-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

/* 消息区 */
.chat__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat__messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.sysline {
  text-align: center;
  margin: 12px 0;
}
.sysline span {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  background: var(--bg-body);
  border-radius: 9px;
  padding: 3px 10px;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.msg-row__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.msg-row__avatar--agent {
  background: linear-gradient(135deg, var(--primary), #2aa198);
}
.msg-row__content {
  max-width: 72%;
  min-width: 0;
}
.msg-row--self {
  justify-content: flex-end;
}
.msg-row--self .msg-row__content {
  text-align: right;
}
.msg-row__tools {
  max-width: 560px;
}
.bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  font-size: var(--font-size-base);
  text-align: left;
}
.bubble--agent {
  background: var(--bg-card);
}
.msg-row--self .bubble {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}
.msg-row__meta {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-top: 4px;
}
.formcard-wrap {
  max-width: 560px;
  margin-bottom: 16px;
}

/* 输入区 */
.chat__input {
  border-top: 1px solid var(--border-color);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chat__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat__toolbar button {
  border: none;
  background: none;
  color: var(--text-regular);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-sm);
  display: inline-flex;
}
.chat__toolbar button:hover {
  background: var(--border-color-light);
  color: var(--primary);
}
.chat__hint {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-left: 4px;
}
.chat__hint code {
  background: var(--bg-body);
  padding: 0 4px;
  border-radius: 3px;
}
.chat__composer {
  position: relative;
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat__composer textarea {
  flex: 1;
  height: 64px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-size: var(--font-size-base);
  resize: none;
  outline: none;
  box-sizing: border-box;
}
.chat__composer textarea:focus {
  border-color: var(--primary);
}
.chat__send {
  border: none;
  background: var(--primary);
  color: #fff;
  padding: 0 22px;
  height: 64px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.chat__send:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
