package org.jeecg.modules.team.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.entity.TeamTaskDependency;

/**
 * @Description: 团队协作-任务依赖 Service（blocked 派生位唯一重算入口）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
public interface ITeamTaskDependencyService extends IService<TeamTaskDependency> {

    /**
     * 添加依赖：taskId 依赖 dependsOnTaskId。
     * 校验：双方任务存在、非自依赖、非重复；成功后重算 taskId 的 blocked
     * （被依赖任务未完成则 blocked=1）
     *
     * @param taskId          依赖方任务主键
     * @param dependsOnTaskId 被依赖任务主键
     */
    void addDependency(String taskId, String dependsOnTaskId);

    /**
     * 移除依赖（不存在视为幂等成功）；成功后重算 taskId 的 blocked
     *
     * @param taskId          依赖方任务主键
     * @param dependsOnTaskId 被依赖任务主键
     */
    void removeDependency(String taskId, String dependsOnTaskId);

    /**
     * 任务依赖的任务列表（被依赖方，含状态快照）
     *
     * @param taskId 任务主键
     * @return 被依赖任务列表
     */
    List<TeamTask> listDependsOnTasks(String taskId);

    /**
     * 是否存在未完成依赖（任一被依赖任务状态 != done）
     *
     * @param taskId 任务主键
     * @return true=存在未完成依赖
     */
    boolean hasUnfinishedDependency(String taskId);

    /**
     * 某任务完成后重算其全部依赖方的 blocked（完成→解除；重做→重新阻塞）
     *
     * @param completedTaskId 刚完成/重开的任务主键
     */
    void refreshDependentsBlocked(String completedTaskId);

    /**
     * 批量查询多个任务的未完成依赖（被依赖任务 status != done），聚合列表专用（防 N+1）。
     * 两查口径：team_task_dependency 按 task_id 批查一次，被依赖任务按 id 批查一次。
     *
     * @param taskIds 任务主键集合
     * @return taskId → 未完成依赖任务 id 列表；无未完成依赖的任务不出现在 key 中
     */
    Map<String, List<String>> mapUnfinishedDependsOnIds(Collection<String> taskIds);
}
