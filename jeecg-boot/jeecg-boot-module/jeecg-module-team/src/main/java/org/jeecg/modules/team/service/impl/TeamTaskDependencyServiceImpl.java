package org.jeecg.modules.team.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.entity.TeamTaskDependency;
import org.jeecg.modules.team.enums.TeamTaskStatus;
import org.jeecg.modules.team.mapper.TeamTaskDependencyMapper;
import org.jeecg.modules.team.service.ITeamTaskDependencyService;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-任务依赖 ServiceImpl（blocked 派生位唯一重算入口）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 *
 * blocked 重算口径：任务存在任一被依赖任务 status != done → blocked=1，否则 blocked=0。
 * 重算只在依赖增删、被依赖任务状态变化两个时机发生，blocked 不对外提供手工改写入口。
 */
@Service
public class TeamTaskDependencyServiceImpl extends ServiceImpl<TeamTaskDependencyMapper, TeamTaskDependency>
        implements ITeamTaskDependencyService {

    @Autowired
    @Lazy
    private ITeamTaskService teamTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDependency(String taskId, String dependsOnTaskId) {
        if (StringUtils.isBlank(taskId) || StringUtils.isBlank(dependsOnTaskId)) {
            throw new JeecgBootException("依赖参数不能为空: taskId=" + taskId + ", dependsOnTaskId=" + dependsOnTaskId);
        }
        if (taskId.equals(dependsOnTaskId)) {
            throw new JeecgBootException("任务不能依赖自身: id=" + taskId);
        }
        TeamTask task = teamTaskService.getById(taskId);
        if (task == null) {
            throw new JeecgBootException("任务不存在: id=" + taskId);
        }
        TeamTask dependsOn = teamTaskService.getById(dependsOnTaskId);
        if (dependsOn == null) {
            throw new JeecgBootException("被依赖任务不存在: id=" + dependsOnTaskId);
        }
        LambdaQueryWrapper<TeamTaskDependency> dup = new LambdaQueryWrapper<>();
        dup.eq(TeamTaskDependency::getTaskId, taskId)
                .eq(TeamTaskDependency::getDependsOnTaskId, dependsOnTaskId);
        if (count(dup) > 0) {
            throw new JeecgBootException("依赖已存在: " + taskId + " → " + dependsOnTaskId);
        }
        TeamTaskDependency row = new TeamTaskDependency();
        row.setTaskId(taskId);
        row.setDependsOnTaskId(dependsOnTaskId);
        row.setCreateTime(new Date());
        save(row);
        refreshBlocked(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDependency(String taskId, String dependsOnTaskId) {
        LambdaQueryWrapper<TeamTaskDependency> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamTaskDependency::getTaskId, taskId)
                .eq(TeamTaskDependency::getDependsOnTaskId, dependsOnTaskId);
        remove(wrapper);
        refreshBlocked(taskId);
    }

    @Override
    public List<TeamTask> listDependsOnTasks(String taskId) {
        LambdaQueryWrapper<TeamTaskDependency> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamTaskDependency::getTaskId, taskId);
        List<String> dependsOnIds = list(wrapper).stream()
                .map(TeamTaskDependency::getDependsOnTaskId)
                .collect(Collectors.toList());
        if (dependsOnIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return teamTaskService.listByIds(dependsOnIds);
    }

    @Override
    public boolean hasUnfinishedDependency(String taskId) {
        LambdaQueryWrapper<TeamTaskDependency> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamTaskDependency::getTaskId, taskId);
        List<String> dependsOnIds = list(wrapper).stream()
                .map(TeamTaskDependency::getDependsOnTaskId)
                .collect(Collectors.toList());
        if (dependsOnIds.isEmpty()) {
            return false;
        }
        LambdaQueryWrapper<TeamTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.in(TeamTask::getId, dependsOnIds)
                .ne(TeamTask::getStatus, TeamTaskStatus.DONE.getCode());
        return teamTaskService.count(taskWrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshDependentsBlocked(String completedTaskId) {
        LambdaQueryWrapper<TeamTaskDependency> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamTaskDependency::getDependsOnTaskId, completedTaskId);
        List<String> dependentTaskIds = list(wrapper).stream()
                .map(TeamTaskDependency::getTaskId)
                .distinct()
                .collect(Collectors.toList());
        for (String dependentTaskId : dependentTaskIds) {
            refreshBlocked(dependentTaskId);
        }
    }

    @Override
    public Map<String, List<String>> mapUnfinishedDependsOnIds(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<TeamTaskDependency> depWrapper = new LambdaQueryWrapper<>();
        depWrapper.in(TeamTaskDependency::getTaskId, taskIds);
        List<TeamTaskDependency> deps = list(depWrapper);
        if (deps.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> dependsOnIds = deps.stream()
                .map(TeamTaskDependency::getDependsOnTaskId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<TeamTask> unfinishedWrapper = new LambdaQueryWrapper<>();
        unfinishedWrapper.in(TeamTask::getId, dependsOnIds)
                .ne(TeamTask::getStatus, TeamTaskStatus.DONE.getCode());
        Set<String> unfinishedIds = teamTaskService.list(unfinishedWrapper).stream()
                .map(TeamTask::getId)
                .collect(Collectors.toSet());
        Map<String, List<String>> result = new HashMap<>();
        for (TeamTaskDependency dep : deps) {
            if (!unfinishedIds.contains(dep.getDependsOnTaskId())) {
                continue;
            }
            result.computeIfAbsent(dep.getTaskId(), k -> new ArrayList<>()).add(dep.getDependsOnTaskId());
        }
        return result;
    }

    /**
     * 重算单个任务的 blocked 派生位（存在任一被依赖任务未完成 → 1，否则 → 0）
     */
    private void refreshBlocked(String taskId) {
        int blocked = hasUnfinishedDependency(taskId) ? 1 : 0;
        TeamTask current = teamTaskService.getById(taskId);
        if (current == null) {
            return;
        }
        Integer currentBlocked = current.getBlocked();
        if (currentBlocked != null && currentBlocked == blocked) {
            return;
        }
        LambdaUpdateWrapper<TeamTask> update = new LambdaUpdateWrapper<>();
        update.eq(TeamTask::getId, taskId)
                .set(TeamTask::getBlocked, blocked)
                .set(TeamTask::getUpdateTime, new Date());
        teamTaskService.update(update);
    }
}
