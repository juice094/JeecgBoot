package org.jeecg.modules.team.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.jeecg.modules.team.service.ITeamBugService;
import org.jeecg.modules.team.service.ITeamIssueService;
import org.jeecg.modules.team.service.ITeamTaskDependencyService;
import org.jeecg.modules.team.service.ITeamTaskLabelService;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.jeecg.modules.team.vo.TeamIssueRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @Description: /issues 跨 kind 聚合查询 ServiceImpl
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 *
 * ponytail: pilot 量级方案——三源各自全量拉取（带过滤）、内存合并排序分页。
 * 量级上千后改单条 SQL UNION ALL 三源 + 窗口排序，内存合并只保留热路径过滤。
 */
@Service
public class TeamIssueServiceImpl implements ITeamIssueService {

    private static final String KIND_TASK = "task";
    private static final String KIND_BUG = "bug";
    private static final String KIND_APPROVAL = "approval";

    /** 统一 open 态的各域落库码（映射表见 TeamIssueRow 注释） */
    private static final List<String> TASK_OPEN = Arrays.asList("todo", "doing");
    private static final List<String> BUG_OPEN = Arrays.asList("open", "processing");
    private static final List<String> APPROVAL_OPEN = Arrays.asList("draft", "pending", "returned");

    @Autowired
    private ITeamTaskService teamTaskService;

    @Autowired
    private ITeamBugService teamBugService;

    @Autowired
    private ITeamApprovalService teamApprovalService;

    @Autowired
    private ITeamTaskLabelService taskLabelService;

    @Autowired
    private ITeamTaskDependencyService taskDependencyService;

    @Override
    public IPage<TeamIssueRow> issuePage(String kind, String keyword, String assigneeId,
            String state, int pageNo, int pageSize) {
        boolean keywordBlank = StringUtils.isBlank(keyword);
        String keywordLower = keywordBlank ? null : keyword.trim().toLowerCase(Locale.ROOT);

        List<TeamIssueRow> merged = new ArrayList<>();
        String k = StringUtils.isBlank(kind) ? "all" : kind.trim().toLowerCase(Locale.ROOT);
        if (KIND_TASK.equals(k) || "all".equals(k)) {
            merged.addAll(queryTasks(assigneeId, state));
        }
        if (KIND_BUG.equals(k) || "all".equals(k)) {
            merged.addAll(queryBugs(assigneeId, state));
        }
        if (KIND_APPROVAL.equals(k) || "all".equals(k)) {
            merged.addAll(queryApprovals(assigneeId, state));
        }

        List<TeamIssueRow> filtered = merged;
        if (!keywordBlank) {
            String kw = keywordLower;
            filtered = merged.stream()
                    .filter(row -> containsIgnoreCase(row.getTitle(), kw)
                            || containsIgnoreCase(row.getCode(), kw))
                    .collect(Collectors.toList());
        }

        filtered.sort(Comparator.comparing(TeamIssueRow::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Page<TeamIssueRow> page = new Page<>(pageNo, pageSize);
        page.setTotal(filtered.size());
        int from = (int) Math.min(filtered.size(), (long) (pageNo - 1) * pageSize);
        int to = (int) Math.min(filtered.size(), (long) pageNo * pageSize);
        page.setRecords(from >= to ? Collections.emptyList() : filtered.subList(from, to));
        return page;
    }

    private List<TeamIssueRow> queryTasks(String assigneeId, String state) {
        LambdaQueryWrapper<TeamTask> wrapper = new LambdaQueryWrapper<>();
        applyStateFilter(wrapper, state, TASK_OPEN, TeamTask::getStatus);
        if (StringUtils.isNotBlank(assigneeId)) {
            wrapper.eq(TeamTask::getAssigneeId, assigneeId);
        }
        List<TeamTask> tasks = teamTaskService.list(wrapper);
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<TeamLabel>> labelMap = taskLabelService.listLabelsByTaskIds(
                tasks.stream().map(TeamTask::getId).collect(Collectors.toList()));
        // blockedBy 批查一次（task_dependency 两查口径），避免聚合行 N+1
        Map<String, List<String>> blockedByMap = taskDependencyService.mapUnfinishedDependsOnIds(
                tasks.stream().map(TeamTask::getId).collect(Collectors.toList()));
        List<TeamIssueRow> rows = new ArrayList<>(tasks.size());
        for (TeamTask task : tasks) {
            TeamIssueRow row = new TeamIssueRow();
            row.setKind(KIND_TASK);
            row.setBizId(task.getId());
            row.setCode(task.getTaskNo() != null ? "T-" + task.getTaskNo() : null);
            row.setTitle(task.getTitle());
            row.setState(toState(TASK_OPEN, task.getStatus()));
            row.setAssigneeName(task.getAssigneeName());
            row.setLabels(labelMap.getOrDefault(task.getId(), Collections.emptyList()));
            row.setBlocked(task.getBlocked() != null && task.getBlocked() == 1);
            row.setBlockedBy(blockedByMap.getOrDefault(task.getId(), Collections.emptyList()));
            row.setCreateTime(task.getCreateTime());
            row.setDueDate(task.getDueDate());
            rows.add(row);
        }
        return rows;
    }

    private List<TeamIssueRow> queryBugs(String assigneeId, String state) {
        LambdaQueryWrapper<TeamBug> wrapper = new LambdaQueryWrapper<>();
        applyStateFilter(wrapper, state, BUG_OPEN, TeamBug::getStatus);
        if (StringUtils.isNotBlank(assigneeId)) {
            // bug 域无处理人 id 列，按提交人过滤（/issues「我提交的」口径）
            wrapper.eq(TeamBug::getReporterId, assigneeId);
        }
        List<TeamBug> bugs = teamBugService.list(wrapper);
        List<TeamIssueRow> rows = new ArrayList<>(bugs.size());
        for (TeamBug bug : bugs) {
            TeamIssueRow row = new TeamIssueRow();
            row.setKind(KIND_BUG);
            row.setBizId(bug.getId());
            row.setCode(bug.getBugNo() != null ? "B-" + bug.getBugNo() : null);
            row.setTitle(bug.getDescription());
            // 处理人为空时回退提交人，保持 assigneeName 列不为空观感
            row.setAssigneeName(StringUtils.isNotBlank(bug.getHandlerName())
                    ? bug.getHandlerName() : bug.getReporterName());
            row.setState(toState(BUG_OPEN, bug.getStatus()));
            row.setLabels(Collections.emptyList());
            row.setBlocked(Boolean.FALSE);
            row.setBlockedBy(Collections.emptyList());
            row.setCreateTime(bug.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    private List<TeamIssueRow> queryApprovals(String assigneeId, String state) {
        LambdaQueryWrapper<TeamApproval> wrapper = new LambdaQueryWrapper<>();
        applyStateFilter(wrapper, state, APPROVAL_OPEN, TeamApproval::getStatus);
        if (StringUtils.isNotBlank(assigneeId)) {
            wrapper.eq(TeamApproval::getApplicantId, assigneeId);
        }
        List<TeamApproval> approvals = teamApprovalService.list(wrapper);
        List<TeamIssueRow> rows = new ArrayList<>(approvals.size());
        for (TeamApproval approval : approvals) {
            TeamIssueRow row = new TeamIssueRow();
            row.setKind(KIND_APPROVAL);
            row.setBizId(approval.getId());
            row.setCode(approval.getCode());
            row.setTitle(approval.getTitle());
            row.setAssigneeName(approval.getApplicantName());
            row.setState(toState(APPROVAL_OPEN, approval.getStatus()));
            row.setLabels(Collections.emptyList());
            row.setBlocked(Boolean.FALSE);
            row.setBlockedBy(Collections.emptyList());
            row.setCreateTime(approval.getCreateTime());
            row.setDueDate(approval.getEndDate());
            rows.add(row);
        }
        return rows;
    }

    private <T> void applyStateFilter(LambdaQueryWrapper<T> wrapper, String state,
            List<String> openCodes, com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> statusField) {
        if (StringUtils.isBlank(state) || "all".equalsIgnoreCase(state.trim())) {
            return;
        }
        String s = state.trim().toLowerCase(Locale.ROOT);
        if ("open".equals(s)) {
            wrapper.in(statusField, openCodes);
        } else if ("closed".equals(s)) {
            wrapper.notIn(statusField, openCodes);
        }
        // 其余非法值不附加过滤（宽容口径，避免误判空结果）
    }

    private String toState(List<String> openCodes, String statusCode) {
        if (statusCode == null) {
            return "closed";
        }
        for (String open : openCodes) {
            if (open.equalsIgnoreCase(statusCode.trim())) {
                return "open";
            }
        }
        return "closed";
    }

    private boolean containsIgnoreCase(String value, String keywordLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keywordLower);
    }
}
