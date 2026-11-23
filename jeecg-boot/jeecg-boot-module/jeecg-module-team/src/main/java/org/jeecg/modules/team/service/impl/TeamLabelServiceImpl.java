package org.jeecg.modules.team.service.impl;

import java.util.List;

import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.entity.TeamTaskLabel;
import org.jeecg.modules.team.mapper.TeamLabelMapper;
import org.jeecg.modules.team.service.ITeamLabelService;
import org.jeecg.modules.team.service.ITeamTaskLabelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-标签 ServiceImpl
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
@Service
public class TeamLabelServiceImpl extends ServiceImpl<TeamLabelMapper, TeamLabel> implements ITeamLabelService {

    @Autowired
    private ITeamTaskLabelService teamTaskLabelService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithLinks(String id) {
        removeTaskLinks(java.util.Collections.singletonList(id));
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatchWithLinks(List<String> ids) {
        removeTaskLinks(ids);
        removeByIds(ids);
    }

    private void removeTaskLinks(List<String> labelIds) {
        LambdaQueryWrapper<TeamTaskLabel> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TeamTaskLabel::getLabelId, labelIds);
        teamTaskLabelService.remove(wrapper);
    }
}
