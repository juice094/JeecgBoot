package org.jeecg.modules.team.service.impl;

import java.util.Date;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.enums.TeamBugStatus;
import org.jeecg.modules.team.mapper.TeamBugMapper;
import org.jeecg.modules.team.service.ITeamBugService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service
public class TeamBugServiceImpl extends ServiceImpl<TeamBugMapper, TeamBug> implements ITeamBugService {

    /** 描述冗余进审计表前的截断上限（表字段 128，留余量） */
    private static final int BIZ_CODE_MAX_LENGTH = 120;

    @Autowired
    private TeamAuditLogger auditLogger;

    @Autowired
    private TeamOwnershipGuard ownershipGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String status) {
        TeamBug bug = getById(id);
        if (bug == null) {
            throw new JeecgBootException("Bug 反馈不存在: id=" + id);
        }
        TeamBugStatus from = TeamBugStatus.fromCode(bug.getStatus());
        if (from == null) {
            throw new JeecgBootException("Bug 反馈状态数据异常: id=" + id + ", 存量状态=" + bug.getStatus());
        }
        TeamBugStatus target = TeamBugStatus.fromCode(status);
        if (target == null) {
            throw new JeecgBootException("非法的目标状态: " + status + "（允许值 open/processing/resolved/closed）");
        }
        if (!from.canTransitTo(target)) {
            throw new JeecgBootException("非法状态流转: " + from.getCode() + " → " + target.getCode()
                    + "（Bug id=" + id + "）");
        }
        bug.setStatus(target.getCode());
        bug.setUpdateTime(new Date());
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            bug.setUpdateBy(loginUser.getUsername());
        }
        updateById(bug);

        auditLogger.log(TeamAuditBizType.BUG, bug.getId(), abbreviate(bug.getDescription()),
                TeamAuditAction.CHANGE_STATUS, from.getCode(), target.getCode(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOne(String id) {
        TeamBug bug = getById(id);
        if (bug == null) {
            throw new JeecgBootException("Bug 反馈不存在: id=" + id);
        }
        ownershipGuard.requireOwnerOrAdmin(bug.getCreateBy(), "他人 Bug 反馈");
        removeById(id);
    }

    private String abbreviate(String description) {
        if (description == null) {
            return null;
        }
        return description.length() <= BIZ_CODE_MAX_LENGTH
                ? description : description.substring(0, BIZ_CODE_MAX_LENGTH) + "...";
    }
}
