<script setup lang="ts">
// 工作邮箱（工具组）—— owner: juice094
// 定位：额外工作线（原型与骨架），非 docs/23 主合同施工项。
// 合同依据：projects/agent-collab-pilot/docs/OA 邮件体系接口文档.md v1.1.0
// 现状：后端 jeecg-module-mail 未立项，/team/mail/** 为契约占位；本页默认 mock，不发起真实请求。
import { computed, ref } from 'vue';
import {
  PhPaperPlaneRight, PhEnvelopeSimple, PhEnvelopeSimpleOpen, PhArrowsClockwise, PhWarningCircle,
} from '@phosphor-icons/vue';
import type { MailCanonicalFolder, MailMessageIndex, MailSendMode } from '@/api/mail';
import { MAIL_FOLDER_META, MAIL_SEND_MODES } from '@/constants/mail';

/** 原型线横幅：明确告诉评审“这是骨架，不是联调基准” */
const CONTRACT_NOTICE = '原型骨架：/team/mail/** 尚未立项，本页数据为 mock，不计入 docs/23 联调基准。';

const folders = MAIL_FOLDER_META;
const activeFolder = ref<MailCanonicalFolder>('Inbox');

const mails = ref<MailMessageIndex[]>([
  {
    id: 5001, accountId: 7, canonicalFolder: 'Inbox', subject: '关于2026年第四季度业务目标的确认通知',
    fromName: '王立柱', fromAddressHash: 'a1b2c3d4', receivedTime: '2026-11-17 10:30:00',
    read: true, hasAttachment: false, snippet: '请各部门于本周五前确认四季度目标口径……',
  },
  {
    id: 5002, accountId: 7, canonicalFolder: 'Inbox', subject: '[系统通知] 您的11月工资条已生成',
    fromName: 'HR 系统', fromAddressHash: 'e5f6a7b8', receivedTime: '2026-11-17 09:00:00',
    read: false, hasAttachment: true, snippet: '工资条已生成，请登录系统查看明细。',
  },
  {
    id: 5003, accountId: 7, canonicalFolder: 'Inbox', subject: '转发：外事学院大数据实验室建设方案',
    fromName: '黄茜茜', fromAddressHash: 'c9d0e1f2', receivedTime: '2026-11-16 18:20:00',
    read: false, hasAttachment: true, snippet: '附上方案初稿，请评估交付节点。',
  },
]);

const loading = ref(false);
const errorMsg = ref('');
const selectedId = ref<number | null>(null);
const detailOpen = ref(false);
const composerOpen = ref(false);
const composerMode = ref<MailSendMode>('new');
const composer = ref({ to: '', subject: '', html: '' });
const syncHint = ref('');

const folderMails = computed(() => mails.value.filter((m) => m.canonicalFolder === activeFolder.value));
const selectedMail = computed(() => mails.value.find((m) => m.id === selectedId.value) || null);

function switchFolder(key: MailCanonicalFolder) {
  activeFolder.value = key;
  selectedId.value = null;
  detailOpen.value = false;
}

function openDetail(mail: MailMessageIndex) {
  selectedId.value = mail.id;
  mail.read = true;
  detailOpen.value = true;
}

function openComposer(mode: MailSendMode, mail?: MailMessageIndex) {
  composerMode.value = mode;
  composer.value = {
    to: mode === 'new' ? '' : (mail?.fromAddressHash ?? ''),
    subject: mode === 'new' ? '' : `Re: ${mail?.subject ?? ''}`,
    html: '',
  };
  composerOpen.value = true;
}

function closeComposer() {
  composerOpen.value = false;
}

/** 原型线：只做本地提示，不调用 sendMailMessage，避免对未立项端点发请求 */
function submitComposer() {
  errorMsg.value = '';
  if (!composer.value.to.trim() || !composer.value.subject.trim()) {
    errorMsg.value = '收件人与主题为必填（原型校验）。';
    return;
  }
  composerOpen.value = false;
  syncHint.value = '原型线：写信未真实投递；后端立项后接 POST /team/mail/messages/send。';
}

function refreshMock() {
  loading.value = true;
  errorMsg.value = '';
  window.setTimeout(() => {
    loading.value = false;
    syncHint.value = '原型线：刷新为 mock 重载；真实同步走 POST /team/mail/accounts/{id}/sync。';
  }, 300);
}
</script>

<template>
  <div class="mail-page">
    <div class="card mail-notice">
      <PhWarningCircle :size="16" />
      <span>{{ CONTRACT_NOTICE }}</span>
    </div>

    <div class="mail-layout">
      <aside class="card mail-sidebar">
        <div class="card-header"><span class="card-title">文件夹</span></div>
        <button
          v-for="f in folders"
          :key="f.key"
          class="mail-folder"
          :class="{ 'mail-folder--active': activeFolder === f.key }"
          @click="switchFolder(f.key)"
        >
          {{ f.label }}
        </button>
        <div class="mail-sidebar__hint">同步状态：mock（lastUid 未接）</div>
      </aside>

      <section class="card mail-list">
        <div class="card-header mail-list__header">
          <span class="card-title">{{ folders.find((f) => f.key === activeFolder)?.label }}</span>
          <span class="mail-list__actions">
            <button class="btn btn--sm" :disabled="loading" @click="refreshMock">
              <PhArrowsClockwise :size="14" /> 刷新
            </button>
            <button class="btn btn-primary btn--sm" @click="openComposer('new')">
              <PhPaperPlaneRight :size="14" /> 写邮件
            </button>
          </span>
        </div>

        <div v-if="errorMsg" class="mail-error">{{ errorMsg }}</div>
        <div v-if="syncHint" class="mail-hint">{{ syncHint }}</div>

        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th width="44"></th><th>发件人</th><th>主题</th><th width="160">时间</th></tr>
            </thead>
            <tbody>
              <tr
                v-for="m in folderMails"
                :key="m.id"
                :class="{ 'mail--unread': !m.read, 'mail--selected': selectedId === m.id }"
                @click="openDetail(m)"
              >
                <td>
                  <PhEnvelopeSimpleOpen v-if="m.read" :size="18" style="color: var(--primary)" />
                  <PhEnvelopeSimple v-else :size="18" />
                </td>
                <td>{{ m.fromName }}</td>
                <td>
                  {{ m.subject }}
                  <span v-if="m.hasAttachment" class="tag tag--info" style="margin-left: 6px">附件</span>
                </td>
                <td>{{ m.receivedTime }}</td>
              </tr>
              <tr v-if="!folderMails.length">
                <td colspan="4" class="mail-empty">当前文件夹暂无邮件（mock）</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="detailOpen && selectedMail" class="card mail-detail">
        <div class="card-header mail-list__header">
          <span class="card-title">邮件详情</span>
          <button class="btn btn--sm" @click="detailOpen = false">收起</button>
        </div>
        <h3 class="mail-detail__subject">{{ selectedMail.subject }}</h3>
        <div class="mail-detail__meta">
          发件人 {{ selectedMail.fromName }}（{{ selectedMail.fromAddressHash }}）· {{ selectedMail.receivedTime }}
        </div>
        <p class="mail-detail__snippet">{{ selectedMail.snippet }}</p>
        <div class="mail-detail__body">
          正文按需 IMAP FETCH（接口文档 §4.3）。原型线不直连 IMAP，正文占位。
        </div>
        <div class="mail-detail__actions">
          <button class="btn btn--sm" @click="openComposer('reply', selectedMail)">回复</button>
          <button class="btn btn--sm" @click="openComposer('replyAll', selectedMail)">回复全部</button>
          <button class="btn btn--sm" @click="openComposer('forward', selectedMail)">转发</button>
        </div>
      </section>
    </div>

    <div v-if="composerOpen" class="card mail-composer">
      <div class="card-header mail-list__header">
        <span class="card-title">{{ MAIL_SEND_MODES.find((m) => m.key === composerMode)?.label }}</span>
        <button class="btn btn--sm" @click="closeComposer">关闭</button>
      </div>
      <div class="form-grid">
        <div class="form-group">
          <div class="form-label">收件人 <span class="req">*</span></div>
          <input v-model="composer.to" type="text" class="form-control" placeholder="name@company.com" />
        </div>
        <div class="form-group">
          <div class="form-label">主题 <span class="req">*</span></div>
          <input v-model="composer.subject" type="text" class="form-control" />
        </div>
      </div>
      <div class="form-group">
        <div class="form-label">正文</div>
        <textarea v-model="composer.html" class="form-control" rows="6" placeholder="原型线：纯文本占位，正式版为 HTML 编辑"></textarea>
      </div>
      <div class="mail-composer__actions">
        <button class="btn btn--sm" @click="closeComposer">取消</button>
        <button class="btn btn-primary btn--sm" @click="submitComposer">
          <PhPaperPlaneRight :size="14" /> 发送（原型）
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mail-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  color: var(--warning);
  font-size: 13px;
}

.mail-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.mail-sidebar {
  padding: 12px;
}

.mail-folder {
  display: block;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 4px;
  text-align: left;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
}

.mail-folder--active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
}

.mail-sidebar__hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-secondary);
}

.mail-list__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mail-list__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.mail-error {
  padding: 8px 16px;
  color: var(--danger);
  font-size: 13px;
}

.mail-hint {
  padding: 8px 16px;
  color: var(--text-secondary);
  font-size: 12px;
}

.mail--unread {
  font-weight: 600;
  background: #f9fbfc;
}

.mail--selected {
  background: var(--primary-light);
}

.mail-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-secondary);
}

.mail-detail {
  grid-column: 1 / -1;
  padding: 16px;
}

.mail-detail__subject {
  margin: 8px 0;
}

.mail-detail__meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.mail-detail__snippet {
  margin: 12px 0;
}

.mail-detail__body {
  padding: 16px;
  background: #f9fbfc;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}

.mail-detail__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.mail-composer {
  padding: 16px;
}

.mail-composer__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
