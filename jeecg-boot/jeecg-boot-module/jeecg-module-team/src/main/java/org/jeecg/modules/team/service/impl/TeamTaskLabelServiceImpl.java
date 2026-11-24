package org.jeecg.modules.team.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.entity.TeamTaskLabel;
import org.jeecg.modules.team.mapper.TeamTaskLabelMapper;
import org.jeecg.modules.team.service.ITeamLabelService;
import org.jeecg.modules.team.service.ITeamTaskLabelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-任务标签关联 ServiceImpl
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
@Service
public class TeamTaskLabelServiceImpl extends ServiceImpl<TeamTaskLabelMapper, TeamTaskLabel>
        implements ITeamTaskLabelService {

    @Autowired
    @Lazy
    private ITeamLabelService labelService;

    @Override
    public Map<String, List<TeamLabel>> listLabelsByTaskIds(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<TeamTaskLabel> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TeamTaskLabel::getTaskId, taskIds);
        List<TeamTaskLabel> links = list(wrapper);
        if (links.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> labelIds = links.stream().map(TeamTaskLabel::getLabelId).collect(Collectors.toSet());
        Map<String, TeamLabel> labelMap = labelService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(TeamLabel::getId, l -> l));
        Map<String, List<TeamLabel>> result = new HashMap<>();
        for (TeamTaskLabel link : links) {
            TeamLabel label = labelMap.get(link.getLabelId());
            if (label != null) {
                result.computeIfAbsent(link.getTaskId(), k -> new ArrayList<>()).add(label);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setTaskLabels(String taskId, List<String> labelIds) {
        LambdaQueryWrapper<TeamTaskLabel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamTaskLabel::getTaskId, taskId);
        remove(wrapper);
        if (labelIds == null || labelIds.isEmpty()) {
            return;
        }
        // LinkedHashSet 去重且保持传入顺序；空值直接丢弃
        Set<String> distinctIds = labelIds.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Date now = new Date();
        // ponytail: 逐行 save（saveBatch 走 SqlSession 批处理，无注入环境不可用）；标签量级小，无性能顾虑
        for (String labelId : distinctIds) {
            TeamTaskLabel row = new TeamTaskLabel();
            row.setTaskId(taskId);
            row.setLabelId(labelId);
            row.setCreateTime(now);
            save(row);
        }
    }
}
