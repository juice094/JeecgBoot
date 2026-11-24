package org.jeecg.modules.team.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.entity.TeamTaskLabel;

/**
 * @Description: 团队协作-任务标签关联 Service
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
public interface ITeamTaskLabelService extends IService<TeamTaskLabel> {

    /**
     * 批量取任务的标签列表（taskId → labels），供 /team/task/list 与 /team/issue/list 并入返回
     *
     * @param taskIds 任务主键集合，空集合返回空 Map
     * @return 任务ID到标签列表的映射（无标签的任务不建 entry）
     */
    Map<String, List<TeamLabel>> listLabelsByTaskIds(Collection<String> taskIds);

    /**
     * 全量替换任务标签（先删后插，同事务）；labelIds 为空表示清空
     *
     * @param taskId   任务主键
     * @param labelIds 标签主键列表（去重、忽略空值；不校验标签是否存在，由调用方保证）
     */
    void setTaskLabels(String taskId, List<String> labelIds);
}
