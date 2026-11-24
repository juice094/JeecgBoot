package org.jeecg.modules.team.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamLabel;

/**
 * @Description: 团队协作-标签 Service
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
public interface ITeamLabelService extends IService<TeamLabel> {

    /**
     * 删除标签并级联清理 team_task_label 关联（同事务）
     *
     * @param id 标签主键
     */
    void deleteWithLinks(String id);

    /**
     * 批量删除标签并级联清理 team_task_label 关联（同事务）
     *
     * @param ids 标签主键列表
     */
    void deleteBatchWithLinks(List<String> ids);
}
