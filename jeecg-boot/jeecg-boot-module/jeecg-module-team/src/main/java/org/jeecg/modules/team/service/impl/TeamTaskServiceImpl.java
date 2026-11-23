package org.jeecg.modules.team.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.enums.TeamTaskStatus;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.jeecg.modules.team.service.ITeamTaskCommentService;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.jeecg.modules.team.vo.TeamTaskBoard;
import org.jeecg.modules.team.vo.TeamTaskDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-任务 ServiceImpl（状态机 + 临期查询）
 * @Author: juice094 (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamTaskServiceImpl extends ServiceImpl<TeamTaskMapper, TeamTask> implements ITeamTaskService {

    /** ponytail: 临期窗口上限，防 days 过大拖垮查询；真实量级上来后改配置化 */
    private static final int MAX_DUE_SOON_DAYS = 90;

    @Autowired
    private TeamAuditLogger auditLogger;

    @Autowired
    private TeamOwnershipGuard ownershipGuard;

    @Lazy
    @Autowired
    private ITeamTaskCommentService commentService;

    @Autowired
    private TeamAuditLogMapper auditLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String status) {
        TeamTask task = getById(id);
        if (task == null) {
            throw new JeecgBootException("任务不存在: id=" + id);
        }
        // F-09：流转限指派人/创建人/管理员（状态机校验先行，未登录上下文跳过归属校验）
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        TeamTaskStatus from = TeamTaskStatus.fromCode(task.getStatus());
        if (from == null) {
            throw new JeecgBootException("任务状态数据异常: id=" + id + ", 存量状态=" + task.getStatus());
        }
        TeamTaskStatus target = TeamTaskStatus.fromCode(status);
        if (target == null) {
            throw new JeecgBootException("非法的目标状态: " + status + "（允许值 todo/doing/done）");
        }
        if (!from.canTransitTo(target)) {
            throw new JeecgBootException("非法状态流转: " + from.getCode() + " → " + target.getCode()
                    + "（任务 id=" + id + "）");
        }
        if (loginUser != null) {
            ownershipGuard.requireTaskOperator(task.getAssigneeId(), task.getAssigneeName(),
                    task.getCreateBy(), id);
        }
        task.setStatus(target.getCode());
        task.setUpdateTime(new Date());
        if (loginUser != null) {
            task.setUpdateBy(loginUser.getUsername());
        }
        updateById(task);

        auditLogger.log(TeamAuditBizType.TASK, task.getId(), task.getTitle(),
                TeamAuditAction.CHANGE_STATUS, from.getCode(), target.getCode(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void edit(TeamTask task) {
        TeamTask entity = requireTask(task.getId());
        // F-09：编辑限创建人或管理员
        ownershipGuard.requireOwnerOrAdmin(entity.getCreateBy(), "他人任务");
        // 状态强制回填：状态变更唯一入口是 changeStatus（与线 A 的 controller 口径一致）
        task.setStatus(entity.getStatus());
        task.setUpdateTime(new Date());
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            task.setUpdateBy(loginUser.getUsername());
        }
        updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOne(String id) {
        TeamTask entity = requireTask(id);
        ownershipGuard.requireOwnerOrAdmin(entity.getCreateBy(), "他人任务");
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(List<String> ids) {
        // 先全量校验归属，任一不过则整体拒绝（避免部分删除）
        List<TeamTask> entities = listByIds(ids);
        if (entities.size() != ids.stream().distinct().count()) {
            throw new JeecgBootException("批量删除存在无效任务: ids=" + ids);
        }
        for (TeamTask entity : entities) {
            ownershipGuard.requireOwnerOrAdmin(entity.getCreateBy(), "他人任务");
        }
        removeByIds(ids);
    }

    private TeamTask requireTask(String id) {
        TeamTask task = getById(id);
        if (task == null) {
            throw new JeecgBootException("任务不存在: id=" + id);
        }
        return task;
    }

    @Override
    public List<TeamTask> dueSoon(int days, String assigneeId) {
        if (days < 0 || days > MAX_DUE_SOON_DAYS) {
            throw new JeecgBootException("临期窗口非法: days=" + days + "（允许 0-" + MAX_DUE_SOON_DAYS + "）");
        }
        Date deadline = Date.from(LocalDate.now().plusDays(days)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        LambdaQueryWrapper<TeamTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(TeamTask::getStatus, TeamTaskStatus.DONE.getCode())
                .isNotNull(TeamTask::getDueDate)
                .le(TeamTask::getDueDate, deadline)
                .orderByAsc(TeamTask::getDueDate)
                .orderByAsc(TeamTask::getSortNo);
        if (StringUtils.isNotBlank(assigneeId)) {
            wrapper.eq(TeamTask::getAssigneeId, assigneeId);
        }
        return list(wrapper);
    }

    @Override
    public TeamTaskDetail detail(String id) {
        TeamTask task = requireTask(id);
        TeamTaskDetail detail = new TeamTaskDetail();
        detail.setTask(task);
        detail.setComments(commentService.listByTask(id));

        // 时间线复用审计日志：只追加不改写，天然就是事件流
        LambdaQueryWrapper<TeamAuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamAuditLog::getBizType, TeamAuditBizType.TASK.getCode())
                .eq(TeamAuditLog::getBizId, id)
                .orderByAsc(TeamAuditLog::getCreateTime);
        detail.setTimeline(auditLogMapper.selectList(wrapper));
        return detail;
    }

    @Override
    public int nextTaskNo() {
        LambdaQueryWrapper<TeamTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TeamTask::getTaskNo).last("LIMIT 1");
        TeamTask latest = getOne(wrapper, false);
        return latest != null && latest.getTaskNo() != null ? latest.getTaskNo() + 1 : 1;
    }

    @Override
    public TeamTaskBoard board() {
        LambdaQueryWrapper<TeamTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TeamTask::getStatus, TeamTaskBoard.boardStatusCodes())
                .orderByAsc(TeamTask::getSortNo)
                .orderByDesc(TeamTask::getCreateTime);
        return TeamTaskBoard.of(list(wrapper));
    }
}
