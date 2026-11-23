package org.jeecg.modules.mail.service;

import java.util.List;

import org.jeecg.modules.mail.api.MailAccountBindingApi;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailAccountBindReqDTO;
import org.jeecg.modules.mail.dto.MailFolderMappingReqDTO;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.jeecg.modules.mail.dto.MailSyncStatusDTO;
import org.jeecg.modules.mail.dto.OaMailAccountRespDTO;
import org.jeecg.modules.mail.dto.OaMailMessageDetailDTO;
import org.jeecg.modules.mail.dto.SendMailMessageReqDTO;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: OA 邮件-员工邮箱 Service（B-1 + B-2 上半）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 契约层 MailAccountBindingApi 的实现者：业务模块只依赖契约层（需求文档 §4.1 依赖规则）。
 * B-1：绑定 + 双测连 + 绑定 CRUD；B-2 上半：文件夹规范映射 + 同步触发/状态（增量同步引擎在 sync 包）。
 * B-2 下半/B-3：列表/详情 FETCH 页面联动，评审后排期。
 */
public interface IOaMailService extends IService<OaMailAccount>, MailAccountBindingApi {

    /**
     * 修改绑定配置（触发双测连，通过后落库）。
     */
    void updateAccount(String id, MailAccountBindReqDTO req);

    /**
     * 手动双测连（用库中已存配置，不落库）。
     */
    ConnectionTestResult testAccount(String id);

    /**
     * 本人账号列表及同步状态（管理员见全部）。
     */
    List<OaMailAccountRespDTO> listAccounts();

    /**
     * 文件夹同步状态列表（含未识别待映射项）。
     */
    List<OaMailFolderState> listFolderStates(String accountId);

    /**
     * 维护文件夹人工映射（未识别文件夹归入 canonical）。
     */
    void saveFolderMapping(String accountId, MailFolderMappingReqDTO req);

    /**
     * 手动触发增量同步（异步，幂等入队）。
     */
    String submitSync(String accountId);

    /**
     * 同步状态（running / lastSyncTime / 文件夹游标）。
     */
    MailSyncStatusDTO syncStatus(String accountId);

    /**
     * 列表查询（只查索引表，不触发 IMAP LIST）。
     *
     * @param accountId       绑定 id（必填）
     * @param canonicalFolder 规范文件夹（可空=全部）
     * @param keyword         主题关键字（可空；LIKE 匹配，试点量级可接受全表扫描）
     * @param unreadOnly      仅未读（null/false 表示不筛选）
     * @param pageNo          页码
     * @param pageSize        每页条数
     */
    IPage<OaMailMessageIndex> pageMessages(String accountId, String canonicalFolder, String keyword,
            Boolean unreadOnly, Integer pageNo, Integer pageSize);

    /**
     * 邮件详情（B-3）：实时按 (文件夹, imapUid) FETCH 正文（text/html + 附件元数据），
     * 打开即回写已读（IMAP \Seen + 索引 read_flag）。
     */
    OaMailMessageDetailDTO getMessageDetail(String id);

    /**
     * 已读/未读置位（B-3）：IMAP \Seen 回写 + 索引列同步。
     */
    void markMessageRead(String id, boolean read);

    /**
     * 删除（B-5）：有「已删除」映射则移入 Trash，否则永久删除；索引行同步删除。
     */
    void deleteMessage(String id);

    /**
     * 写信发送（B-6）：经绑定账号 SMTP 直发；发后 best-effort 追加「已发送」。
     */
    String sendMessage(SendMailMessageReqDTO req);

    // ---- MailAccountBindingApi 对齐说明 ----
    // bind(MailAccountBindReqDTO)        -> 绑定：双测连通过才落库（B-1 全量）
    // unbind(String accountId)           -> 解绑（索引保留策略属 B-2+，当前直接删除绑定记录）
    // testConnection(MailServerConfig)   -> 仅测连（对账/预检用），不落库

    /**
     * 仅测连便捷重载：同时给出 IMAP 与 SMTP 配置。
     */
    ConnectionTestResult testConnection(MailServerConfig imap, MailServerConfig smtp);
}
