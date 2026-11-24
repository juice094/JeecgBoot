package org.jeecg.modules.team.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamAuditLog;

/**
 * @Description: 团队协作-审计日志 Service（只读查询；写入统一走 TeamAuditLogger 埋点）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public interface ITeamAuditLogService extends IService<TeamAuditLog> {

    /**
     * 事件流-回放：取最新 limit 条（create_time 倒序取数后在内存翻转为正序，供按时间线顺序推送）
     *
     * @param bizTypes 业务类型过滤（null/空=不过滤）
     * @param limit    最大条数（调用方须已夹取到合法范围）
     * @return 按时间正序的事件列表
     */
    List<TeamAuditLog> listLatestForStream(List<String> bizTypes, int limit);

    /**
     * 事件流-增量：取 id 游标之后的 limit 条（id 正序）
     *
     * @param lastId   游标（null=从当前最大 id 起步，不回放存量）
     * @param bizTypes 业务类型过滤（null/空=不过滤）
     * @param limit    单轮最大条数（调用方须已夹取到合法范围）
     * @return 按 id 正序的新增事件
     */
    List<TeamAuditLog> listSinceId(String lastId, List<String> bizTypes, int limit);

    /**
     * 事件流-当前最大审计行 id（空表返回 null）
     *
     * @param bizTypes 业务类型过滤（null/空=不过滤）
     */
    String findMaxId(List<String> bizTypes);
}
