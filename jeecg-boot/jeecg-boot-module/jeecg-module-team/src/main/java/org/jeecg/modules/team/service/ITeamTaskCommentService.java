package org.jeecg.modules.team.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamTaskComment;

/**
 * @Description: 团队协作-任务评论 Service
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public interface ITeamTaskCommentService extends IService<TeamTaskComment> {

    /**
     * 按任务查评论，按创建时间升序（时间线正序阅读）
     *
     * @param taskId 任务主键
     * @return 评论列表
     */
    List<TeamTaskComment> listByTask(String taskId);

    /**
     * 新增评论：校验任务存在、正文非空，写入作者快照并埋点
     *
     * @param taskId 任务主键
     * @param body   评论正文（Markdown）
     * @return 新建的评论
     */
    TeamTaskComment addComment(String taskId, String body);
}
