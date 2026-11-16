<script setup lang="ts">
// 聊天工作台 —— owner: qingyanjunzi
// 职责：单聊/群聊会话列表、消息收发、在线状态、语音消息入口
// 当前为带示例数据的页面骨架；接口对接（conversations/messages/inbox）见 issue #1
import { ref } from 'vue';

interface Conversation {
  id: string;
  name: string;
  lastMsg: string;
  time: string;
  unread: number;
  online?: boolean;
}

const conversations = ref<Conversation[]>([
  { id: '1', name: '需求设计（群）', lastMsg: '表单字段我补一下', time: '10:24', unread: 2 },
  { id: '2', name: 'Ym3699', lastMsg: '组织树接口联调好了', time: '09:58', unread: 0, online: true },
  { id: '3', name: '开发联调（群）', lastMsg: '[文件] 接口约定.md', time: '昨天', unread: 0 },
  { id: '4', name: 'qingyanjunzi', lastMsg: '收到', time: '昨天', unread: 0, online: true },
]);

const messages = ref([
  { id: 'm1', from: 'Ym3699', self: false, text: '组织树接口联调好了，你那边看下面板数据？', time: '09:55' },
  { id: 'm2', from: 'admin', self: true, text: '好，我拉一下最新代码看看', time: '09:56' },
  { id: 'm3', from: 'Ym3699', self: false, text: '记得用 admin token，部门接口要权限', time: '09:58' },
]);

const currentId = ref('2');
const draft = ref('');

function send() {
  if (!draft.value.trim()) return;
  messages.value.push({ id: `m${Date.now()}`, from: 'admin', self: true, text: draft.value, time: '现在' });
  draft.value = '';
}
</script>

<template>
  <div class="chat">
    <!-- 会话列表 -->
    <aside class="chat__list">
      <div class="chat__list-search">
        <input placeholder="搜索会话 / 成员" />
      </div>
      <div
        v-for="c in conversations"
        :key="c.id"
        class="conv"
        :class="{ 'conv--active': c.id === currentId }"
        @click="currentId = c.id"
      >
        <div class="conv__avatar">
          {{ c.name[0] }}
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
      <div class="chat__messages">
        <div v-for="m in messages" :key="m.id" class="msg" :class="{ 'msg--self': m.self }">
          <div class="msg__bubble">{{ m.text }}</div>
          <div class="msg__meta">{{ m.from }} · {{ m.time }}</div>
        </div>
      </div>
      <div class="chat__input">
        <div class="chat__toolbar">
          <button title="发送文件">📎</button>
          <button title="语音消息">🎤</button>
        </div>
        <textarea v-model="draft" placeholder="输入消息，Enter 发送" @keydown.enter.prevent="send" />
        <button class="chat__send" @click="send">发送</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  height: 100%;
  gap: 16px;
}

.chat__list {
  width: 260px;
  flex-shrink: 0;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
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
  font-size: 13px;
  outline: none;
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
  font-size: 14px;
  font-weight: 600;
}

.conv__time {
  font-size: 11px;
  color: var(--text-muted);
}

.conv__last {
  font-size: 12px;
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
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

.chat__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  min-width: 0;
}

.chat__messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.msg {
  max-width: 70%;
}

.msg--self {
  align-self: flex-end;
  text-align: right;
}

.msg__bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--border-color-light);
  font-size: 14px;
  text-align: left;
}

.msg--self .msg__bubble {
  background: var(--primary);
  color: #fff;
}

.msg__meta {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.chat__input {
  border-top: 1px solid var(--border-color-light);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat__toolbar {
  display: flex;
  gap: 8px;
}

.chat__toolbar button {
  border: none;
  background: none;
  font-size: 16px;
  cursor: pointer;
}

.chat__input textarea {
  height: 64px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font-size: 14px;
  resize: none;
  outline: none;
}

.chat__send {
  align-self: flex-end;
  border: none;
  background: var(--primary);
  color: #fff;
  padding: 7px 22px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
</style>
