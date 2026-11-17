package org.jeecg.modules.team.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.entity.TeamTaskComment;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamTaskCommentMapper;
import org.jeecg.modules.team.service.ITeamTaskCommentService;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-任务评论 ServiceImpl
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamTaskCommentServiceImpl extends ServiceImpl<TeamTaskCommentMapper, TeamTaskComment>
        implements ITeamTaskCommentService {

    /** 正文长度上限，与库表 TEXT 容量和前端输入框限制对齐 */
    private static final int MAX_BODY_LENGTH = 10000;

    @Autowired
    private ITeamTaskService teamTaskService;

    @Autowired
    private TeamAuditLogger auditLogger;

    @Override
    public List<TeamTaskComment> listByTask(String taskId) {
        if (StringUtils.isBlank(taskId)) {
            throw new JeecgBootException("任务ID不能为空");
        }
        LambdaQueryWrapper<TeamTaskComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamTaskComment::getTaskId, taskId)
                .orderByAsc(TeamTaskComment::getCreateTime);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamTaskComment addComment(String taskId, String body) {
        if (StringUtils.isBlank(taskId)) {
            throw new JeecgBootException("任务ID不能为空");
        }
        if (StringUtils.isBlank(body)) {
            throw new JeecgBootException("评论内容不能为空");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new JeecgBootException("评论内容过长（上限 " + MAX_BODY_LENGTH + " 字符）");
        }

        TeamTask task = teamTaskService.getById(taskId);
        if (task == null) {
            throw new JeecgBootException("任务不存在: id=" + taskId);
        }

        TeamTaskComment comment = new TeamTaskComment();
        comment.setTaskId(taskId);
        comment.setBody(body);
        comment.setCreateTime(new Date());

        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            comment.setAuthorId(loginUser.getId());
            comment.setAuthorName(loginUser.getRealname());
            comment.setCreateBy(loginUser.getUsername());
        } else {
            comment.setAuthorName("系统");
        }

        save(comment);

        // 时间线埋点：评论事件只记元数据，正文在评论表
        auditLogger.log(TeamAuditBizType.TASK, taskId, task.getTitle(),
                TeamAuditAction.COMMENT, null, comment.getId(), null);

        return comment;
    }
}
