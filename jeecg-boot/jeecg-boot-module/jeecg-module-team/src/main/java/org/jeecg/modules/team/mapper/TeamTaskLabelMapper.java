package org.jeecg.modules.team.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.team.entity.TeamTaskLabel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: 团队协作-任务标签关联 Mapper
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
@Mapper
public interface TeamTaskLabelMapper extends BaseMapper<TeamTaskLabel> {
}
