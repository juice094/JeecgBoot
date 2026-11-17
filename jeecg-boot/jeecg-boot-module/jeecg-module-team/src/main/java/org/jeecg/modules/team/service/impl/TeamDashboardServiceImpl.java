package org.jeecg.modules.team.service.impl;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamBugStatus;
import org.jeecg.modules.team.enums.TeamTaskStatus;
import org.jeecg.modules.team.mapper.TeamApprovalMapper;
import org.jeecg.modules.team.mapper.TeamBugMapper;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.service.ITeamDashboardService;
import org.jeecg.modules.team.vo.TeamDashboardSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * @Description: 团队协作-仪表盘聚合 ServiceImpl
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.1
 *
 * ponytail: 三计数走各自 mapper 的 count，单接口聚合省前端 N 次往返。
 *
 * sys 侧聚合（D-72 宿裁授权例外，报告留痕）：
 * - 越出口理由：ISysBaseAPI（jeecg-system-local-api）无公告/未读计数方法，
 *   为免前端 N 次往返，宿裁许可 team 域 dashboard 直读 sys 表，仅此一处；
 * - 未读口径：sys_announcement_send（用户通告阅读标记表）中 user_id=当前登录人
 *   且 read_flag=0 的行数；公告发布时 jeecg 会向该表写入接收人行（全体/指定均落行）；
 * - 最新公告口径：sys_announcement 中 del_flag='0' 且 send_status='1'（已发布），
 *   按 send_time 倒序取前 5 条（send_time 为空的排后）；
 * - 与 F-10 的关系：F-10（/sys/message/sysMessage/list 510）涉及的 sys_sms 是推送日志，
 *   与本口径无关，不构成依赖。
 */
@Service
public class TeamDashboardServiceImpl implements ITeamDashboardService {

    private static final String STATUS_PENDING = "pending";

    /** 最新公告条数上限 */
    private static final int LATEST_NOTICE_LIMIT = 5;

    @Autowired
    private TeamApprovalMapper approvalMapper;

    @Autowired
    private TeamTaskMapper taskMapper;

    @Autowired
    private TeamBugMapper bugMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public TeamDashboardSummary summarize(String loginUserId) {
        if (StringUtils.isBlank(loginUserId)) {
            throw new JeecgBootException("仪表盘聚合需要登录用户上下文");
        }
        TeamDashboardSummary summary = new TeamDashboardSummary();

        LambdaQueryWrapper<TeamApproval> approvalWrapper = new LambdaQueryWrapper<>();
        approvalWrapper.eq(TeamApproval::getStatus, STATUS_PENDING);
        summary.setPendingApprovalCount(approvalMapper.selectCount(approvalWrapper));

        LambdaQueryWrapper<TeamTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(TeamTask::getAssigneeId, loginUserId)
                .in(TeamTask::getStatus,
                        Arrays.asList(TeamTaskStatus.TODO.getCode(), TeamTaskStatus.DOING.getCode()));
        summary.setMyUnfinishedTaskCount(taskMapper.selectCount(taskWrapper));

        LambdaQueryWrapper<TeamBug> bugWrapper = new LambdaQueryWrapper<>();
        bugWrapper.in(TeamBug::getStatus, Arrays.asList(
                TeamBugStatus.OPEN.getCode(), TeamBugStatus.PROCESSING.getCode(), TeamBugStatus.RESOLVED.getCode()));
        summary.setOpenBugCount(bugMapper.selectCount(bugWrapper));

        summary.setUnreadNoticeCount(countUnreadNotices(loginUserId));
        summary.setLatestNotices(queryLatestNotices());
        return summary;
    }

    /** 未读公告数：sys_announcement_send read_flag=0（口径见类注释） */
    private Long countUnreadNotices(String loginUserId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_announcement_send WHERE user_id = ? AND read_flag = 0",
                Long.class, loginUserId);
        return count != null ? count : 0L;
    }

    /** 最新已发布公告（send_time 倒序，空值排后，取前 5） */
    private List<TeamDashboardSummary.NoticeItem> queryLatestNotices() {
        return jdbcTemplate.query(
                "SELECT id, titile, sender, DATE_FORMAT(send_time, '%Y-%m-%d %H:%i:%s') AS send_time "
                        + "FROM sys_announcement WHERE del_flag = '0' AND send_status = '1' "
                        + "ORDER BY send_time IS NULL, send_time DESC LIMIT " + LATEST_NOTICE_LIMIT,
                (rs, rowNum) -> {
                    TeamDashboardSummary.NoticeItem item = new TeamDashboardSummary.NoticeItem();
                    item.setId(rs.getString("id"));
                    item.setTitile(rs.getString("titile"));
                    item.setSender(rs.getString("sender"));
                    item.setSendTime(rs.getString("send_time"));
                    return item;
                });
    }
}
