package org.jeecg.modules.team.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamBug;

/**
 * 团队协作-Bug 反馈 Service
 */
public interface ITeamBugService extends IService<TeamBug> {

    /**
     * 状态流转：按状态机校验目标状态与流转路径，非法流转抛 JeecgBootException
     *
     * @param id     Bug 主键
     * @param status 目标状态码（open/processing/resolved/closed）
     */
    void changeStatus(String id, String status);

    /**
     * 逻辑删除（F-09：限提交人或管理员；D-69a：deleted=1 不物理删）
     */
    void deleteOne(String id);
}
