<script setup lang="ts">
// 工作邮箱（工具组）—— owner: juice094
// 合同依据：projects/agent-collab-pilot/docs/OA 邮件体系接口文档.md v1.1.0
// 现状：已接真实接口（列表/文件夹/同步/账号）；详情、已读、删除、写信对应后端 B-3/B-5/B-6 未实现，保持本地交互并显式提示。
// UI 结构对齐 Outlook 网页版三栏（规格见 .refs/extracted/outlook-mail-spec.md）。
import { computed, onMounted, ref } from 'vue';
import { PhArrowsClockwise, PhEnvelopeSimple, PhWarningCircle } from '@phosphor-icons/vue';
import MailFolderNav from '@/components/MailFolderNav.vue';
import MailListToolbar from '@/components/MailListToolbar.vue';
import MailListRow from '@/components/MailListRow.vue';
import MailReadingPane from '@/components/MailReadingPane.vue';
import MailComposeDialog from '@/components/MailComposeDialog.vue';
import type { MailCanonicalFolder, MailMessageIndex, MailSendMode, MailSyncStatus, MyMailAccount, MailFolder } from '@/api/mail';
import {
  getMailSyncStatus,
  listMailFolders,
  listMailMessages,
  listMyMailAccounts,
  triggerMailSync,
} from '@/api/mail';
import { MAIL_FOLDER_META, MAIL_SEND_MODES } from '@/constants/mail';

/** 未实现能力提示（后端 B-3/B-5/B-6 范围） */
const UNIMPLEMENTED_NOTICE = '详情正文、标记已读、删除、写信接口属后端 B-3/B-5/B-6，尚未实现；当前为本地占位。';

const activeFolder = ref<MailCanonicalFolder>('Inbox');
const mails = ref<MailMessageIndex[]>([]);
const myAccount = ref<MyMailAccount | null>(null);
const folderStates = ref<MailFolder[]>([]);
const syncStatus = ref<MailSyncStatus | null>(null);

const loading = ref(false);
const bootstrapping = ref(true);
const errorMsg = ref('');
const syncHint = ref('');
const selectedId = ref<string | null>(null);
const composerOpen = ref(false);
const composerMode = ref<MailSendMode>('new');
const composer = ref({ to: '', subject: '', html: '' });
const selectedIds = ref<string[]>([]);

// ---- 查件：主题关键字 + 未读筛选（服务端过滤，keyword 仅匹配主题）----
const keyword = ref('');
const unreadOnly = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

/** 输入防抖 300ms，避免每个字符都打一次请求 */
function onKeywordInput() {
  if (searchTimer) clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    selectedId.value = null;
    selectedIds.value = [];
    void loadMessages();
  }, 300);
}

function toggleUnreadOnly() {
  unreadOnly.value = !unreadOnly.value;
  selectedId.value = null;
  selectedIds.value = [];
  void loadMessages();
}

function clearKeyword() {
  keyword.value = '';
  void loadMessages();
}

// ---- 文件夹窗格 ----
const folderNavItems = computed(() =>
  MAIL_FOLDER_META.map((f) => ({
    key: f.key,
    label: f.label,
    count: mails.value.filter((m) => m.canonicalFolder === f.key && !m.readFlag).length || undefined,
  })),
);

const folderMails = computed(() =>
  mails.value
    .filter((m) => m.canonicalFolder === activeFolder.value)
    .slice()
    .sort((a, b) => (b.receivedTime || '').localeCompare(a.receivedTime || '')),
);
const selectedMail = computed(() => mails.value.find((m) => m.id === selectedId.value) || null);
const allChecked = computed(
  () => folderMails.value.length > 0 && selectedIds.value.length === folderMails.value.length,
);

/** 同步状态摘要：取各文件夹最大 lastUid */
const syncSummary = computed(() => {
  if (!syncStatus.value) return '';
  const uid = syncStatus.value.folders?.reduce((max, f) => Math.max(max, f.lastUid ?? 0), 0) ?? 0;
  const time = syncStatus.value.lastSyncTime ?? '—';
  return `lastUid=${uid} · ${time}${syncStatus.value.running ? ' · 同步中' : ''}`;
});

function switchFolder(key: string) {
  activeFolder.value = key as MailCanonicalFolder;
  selectedId.value = null;
  selectedIds.value = [];
  void loadMessages();
}

// ---- 数据加载 ----

async function bootstrap() {
  bootstrapping.value = true;
  errorMsg.value = '';
  try {
    const accounts = await listMyMailAccounts();
    const list = accounts.result ?? [];
    myAccount.value = list[0] ?? null;
    if (!myAccount.value) {
      bootstrapping.value = false;
      return;
    }
    await Promise.all([loadMessages(), loadFolders(), loadSyncStatus()]);
  } catch (e) {
    errorMsg.value = `加载邮箱失败：${(e as Error).message}`;
  } finally {
    bootstrapping.value = false;
  }
}

async function loadMessages() {
  if (!myAccount.value) return;
  loading.value = true;
  errorMsg.value = '';
  try {
    const resp = await listMailMessages({
      accountId: myAccount.value.id,
      canonicalFolder: activeFolder.value,
      keyword: keyword.value.trim() || undefined,
      unreadOnly: unreadOnly.value || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    mails.value = resp.result?.records ?? [];
  } catch (e) {
    errorMsg.value = `加载邮件列表失败：${(e as Error).message}`;
    mails.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadFolders() {
  if (!myAccount.value) return;
  try {
    const resp = await listMailFolders(myAccount.value.id);
    folderStates.value = resp.result ?? [];
  } catch (e) {
    errorMsg.value = `加载文件夹失败：${(e as Error).message}`;
  }
}

async function loadSyncStatus() {
  if (!myAccount.value) return;
  try {
    const resp = await getMailSyncStatus(myAccount.value.id);
    syncStatus.value = resp.result ?? null;
  } catch {
    syncStatus.value = null;
  }
}

async function refresh() {
  if (!myAccount.value) {
    await bootstrap();
    return;
  }
  syncHint.value = '';
  errorMsg.value = '';
  try {
    await triggerMailSync(myAccount.value.id);
    syncHint.value = '已触发增量同步，稍后刷新查看结果。';
    await loadSyncStatus();
  } catch (e) {
    errorMsg.value = `触发同步失败：${(e as Error).message}`;
  }
}

// ---- 列表：全选 / 行交互 ----
function toggleAll(checked: boolean) {
  selectedIds.value = checked ? folderMails.value.map((m) => m.id) : [];
}

function toggleSelect(id: string, checked: boolean) {
  selectedIds.value = checked
    ? [...selectedIds.value, id]
    : selectedIds.value.filter((x) => x !== id);
}

function openDetail(mail: MailMessageIndex) {
  selectedId.value = mail.id;
  // 后端 PATCH /read 未实现（B-3），此处仅本地置位，不落库
  mail.readFlag = true;
}

/** 时间列：今天显时刻，历史显日期（Outlook 口径） */
function rowTime(t: string): string {
  if (!t) return '';
  const d = new Date(t.replace(' ', 'T'));
  const stamp = (x: Date) => x.getFullYear() * 10000 + x.getMonth() * 100 + x.getDate();
  if (!isNaN(d.getTime()) && stamp(d) === stamp(new Date())) return t.slice(11, 16);
  return t.slice(5, 10);
}

// ---- 命令栏动作（后端未实现，保持本地） ----
function markAllRead() {
  folderMails.value.forEach((m) => (m.readFlag = true));
  syncHint.value = `本地已标记；${UNIMPLEMENTED_NOTICE}`;
}

function removeSelected() {
  if (!selectedIds.value.length) return;
  const n = selectedIds.value.length;
  mails.value = mails.value.filter((m) => !selectedIds.value.includes(m.id));
  if (selectedId.value !== null && selectedIds.value.includes(selectedId.value)) {
    selectedId.value = null;
  }
  selectedIds.value = [];
  syncHint.value = `本地已移除 ${n} 封；${UNIMPLEMENTED_NOTICE}`;
}

// ---- 写信 ----
function openComposer(mode: MailSendMode, mail?: MailMessageIndex) {
  composerMode.value = mode;
  composer.value = {
    to: '',
    subject: mode === 'new' ? '' : `Re: ${mail?.subject ?? ''}`,
    html: '',
  };
  composerOpen.value = true;
}

function submitComposer(payload: { to: string; subject: string; html: string }) {
  errorMsg.value = '';
  composer.value = { to: payload.to, subject: payload.subject, html: payload.html };
  if (!payload.to.trim() || !payload.subject.trim()) {
    errorMsg.value = '收件人与主题为必填。';
    return;
  }
  composerOpen.value = false;
  syncHint.value = `未投递；${UNIMPLEMENTED_NOTICE}`;
}

onMounted(bootstrap);
</script>

<template>
  <div class="mail-page">
    <div v-if="errorMsg" class="card mail-notice mail-notice--error">
      <PhWarningCircle :size="16" />
      <span>{{ errorMsg }}</span>
    </div>

    <div v-if="bootstrapping" class="card mail-empty-state">正在加载邮箱…</div>

    <div v-else-if="!myAccount" class="card mail-empty-state">
      <PhEnvelopeSimple :size="40" />
      <p>尚未绑定企业邮箱</p>
      <p class="mail-empty-state__hint">请先通过绑定接口配置 IMAP 与 SMTP（需双测连通过）。</p>
    </div>

    <template v-else>
      <MailListToolbar>
        <template #primary>
          <button type="button" class="btn btn-primary btn--sm" @click="openComposer('new')">
            <PhEnvelopeSimple :size="14" /> 新邮件
          </button>
        </template>
        <template #actions>
          <button type="button" class="cmd-btn" :disabled="!selectedIds.length" @click="removeSelected">
            删除{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
          </button>
          <span class="cmd-divider" />
          <button type="button" class="cmd-btn" @click="markAllRead">全部标记为已读</button>
          <button type="button" class="cmd-btn" :disabled="loading" @click="refresh">
            <PhArrowsClockwise :size="14" /> 刷新
          </button>
        </template>
        <template #right>
          <span v-if="syncHint" class="mail-bar-hint">{{ syncHint }}</span>
          <button
            type="button"
            class="cmd-btn"
            :class="{ 'cmd-btn--active': unreadOnly }"
            :aria-pressed="unreadOnly"
            @click="toggleUnreadOnly"
          >
            仅未读
          </button>
          <span class="mail-search">
            <input
              v-model="keyword"
              type="search"
              class="mail-search__input"
              placeholder="搜索主题…"
              aria-label="按主题搜索邮件"
              maxlength="100"
              @input="onKeywordInput"
            />
            <button
              v-if="keyword"
              type="button"
              class="mail-search__clear"
              aria-label="清空搜索"
              @click="clearKeyword"
            >×</button>
          </span>
        </template>
      </MailListToolbar>

      <div class="mail-layout">
        <MailFolderNav v-model="activeFolder" :folders="folderNavItems" @update:model-value="switchFolder">
          <template #sections>
            <div class="mail-nav-hint">同步：{{ syncSummary || '未同步' }}</div>
            <div v-if="folderStates.some((f) => !f.canonical)" class="mail-nav-hint mail-nav-hint--warn">
              有 {{ folderStates.filter((f) => !f.canonical).length }} 个文件夹未识别，需映射
            </div>
          </template>
        </MailFolderNav>

        <section class="mail-list-pane">
          <div class="mail-list-pane__head">
            <input
              type="checkbox"
              class="mail-list-pane__check-all"
              :checked="allChecked"
              aria-label="全选"
              @change="toggleAll(($event.target as HTMLInputElement).checked)"
            />
            <span class="mail-list-pane__total">共 {{ folderMails.length }} 封</span>
          </div>

          <div v-if="loading" class="mail-empty">加载中…</div>
          <ul v-else class="mail-rows">
            <MailListRow
              v-for="m in folderMails"
              :key="m.id"
              :sender="m.fromName"
              :subject="m.subject"
              :snippet="m.snippet"
              :read="m.readFlag"
              :selected="selectedIds.includes(m.id)"
              :time="rowTime(m.receivedTime)"
              :attachment="m.hasAttachment"
              @open="openDetail(m)"
              @toggle="(checked) => toggleSelect(m.id, checked)"
            />
            <li v-if="!folderMails.length" class="mail-empty">当前文件夹暂无邮件</li>
          </ul>
        </section>

        <MailReadingPane
          v-if="selectedMail"
          :subject="selectedMail.subject"
          :sender-name="selectedMail.fromName"
          :sender-address="selectedMail.fromAddressHash"
          :recipient-line="selectedMail.toSummary || '—'"
          :time="selectedMail.receivedTime"
          :notice="UNIMPLEMENTED_NOTICE"
          notice-action-text="知道了"
          :body="selectedMail.snippet"
          @reply="openComposer('reply', selectedMail)"
          @reply-all="openComposer('replyAll', selectedMail)"
          @forward="openComposer('forward', selectedMail)"
          @notice-action="syncHint = ''"
        />
        <section v-else class="mail-reader-empty">
          <PhEnvelopeSimple :size="40" />
          <p>选择一封邮件以阅读</p>
        </section>
      </div>

      <MailComposeDialog
        :open="composerOpen"
        :title="MAIL_SEND_MODES.find((m) => m.key === composerMode)?.label || '新邮件'"
        :initial="{ to: composer.to, subject: composer.subject }"
        :sender-hint="myAccount.email"
        @submit="submitComposer"
        @close="composerOpen = false"
      />
    </template>
  </div>
</template>

<style scoped>
.mail-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.mail-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  color: var(--warning);
  font-size: 13px;
}

.mail-notice--error {
  color: var(--danger, #d03050);
}

.mail-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  min-height: 320px;
  color: var(--text-muted);
}

.mail-empty-state__hint {
  font-size: var(--font-size-sm);
}

.mail-layout {
  display: grid;
  grid-template-columns: 220px 320px minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
}

.mail-nav-hint {
  padding: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.mail-nav-hint--warn {
  color: var(--warning);
}

.mail-list-pane {
  display: flex;
  flex-direction: column;
  min-height: 420px;
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-right: none;
  border-radius: 0 0 0 var(--radius-md);
}

.mail-list-pane__head {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  height: 40px;
  padding: 0 var(--spacing-lg);
  border-bottom: 1px solid var(--border-color);
}

.mail-list-pane__check-all {
  width: 14px;
  height: 14px;
  accent-color: var(--primary);
  cursor: pointer;
}

.mail-list-pane__total {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.mail-rows {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
}

.mail-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
}

.mail-bar-hint {
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 查件：主题搜索 + 未读切换 */
.cmd-btn--active {
  color: var(--primary);
  font-weight: 600;
}

.mail-search {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.mail-search__input {
  width: 200px;
  height: 28px;
  padding: 0 24px 0 10px;
  font-size: var(--font-size-sm);
  color: var(--text-regular);
  background: var(--bg-body);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color 0.2s, width 0.2s;
}

.mail-search__input:focus {
  width: 260px;
  border-color: var(--primary);
}

.mail-search__clear {
  position: absolute;
  right: 4px;
  width: 18px;
  height: 18px;
  padding: 0;
  line-height: 1;
  font-size: 14px;
  color: var(--text-muted);
  background: transparent;
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.mail-search__clear:hover {
  color: var(--text-regular);
  background: var(--border-color-light);
}

.mail-reader-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-lg);
  min-height: 420px;
  color: var(--text-placeholder);
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-left: none;
  border-radius: 0 0 var(--radius-md) 0;
  font-size: var(--font-size-base);
}

@media (max-width: 1100px) {
  .mail-layout {
    grid-template-columns: 200px minmax(0, 1fr);
  }
  .mail-list-pane {
    border-radius: 0 0 var(--radius-md) var(--radius-md);
    border-right: 1px solid var(--border-color-light);
  }
  .mail-page .mail-layout > :last-child {
    grid-column: 1 / -1;
  }
}
</style>
