package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.jeecg.modules.team.service.ITeamBugService;
import org.jeecg.modules.team.service.ITeamTaskLabelService;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.jeecg.modules.team.vo.TeamIssueRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * /issues 跨 kind 聚合查询单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖：
 * 1) 三源统一行映射（code/state/assigneeName/blocked/dueDate/labels）；
 * 2) create_time desc 全排后内存分页（total 为过滤后总数）；
 * 3) keyword 命中标题与展示编号（T-12/B-3/OA 单号）；
 * 4) state=open/closed 过滤下发到各域 status 条件；
 * 5) assigneeId 按各域字段过滤（task→assignee_id，bug→reporter_id，approval→applicant_id）；
 * 6) kind=bug 时其余两源不查询。
 *
 * id 用 ReflectionTestUtils 写父类字段（Lombok 继承 setter 测试编译期解析问题，同既有测试惯例）。
 */
class TeamIssueServiceTest {

    private Date time(int offsetSeconds) {
        return new Date(1_700_000_000_000L + offsetSeconds * 1000L);
    }

    private TeamTask task(String id, Integer taskNo, String title, String status,
            Integer blocked, Date createTime, String assigneeName) {
        TeamTask t = new TeamTask();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTaskNo(taskNo);
        t.setTitle(title);
        t.setStatus(status);
        t.setBlocked(blocked);
        t.setCreateTime(createTime);
        t.setAssigneeName(assigneeName);
        return t;
    }

    private TeamBug bug(String id, Integer bugNo, String description, String status,
            Date createTime, String reporterName, String handlerName) {
        TeamBug b = new TeamBug();
        ReflectionTestUtils.setField(b, "id", id);
        b.setBugNo(bugNo);
        b.setDescription(description);
        b.setStatus(status);
        b.setCreateTime(createTime);
        b.setReporterName(reporterName);
        b.setHandlerName(handlerName);
        return b;
    }

    private TeamApproval approval(String id, String code, String title, String status,
            Date createTime, String applicantName) {
        TeamApproval a = new TeamApproval();
        ReflectionTestUtils.setField(a, "id", id);
        a.setCode(code);
        a.setTitle(title);
        a.setStatus(status);
        a.setCreateTime(createTime);
        a.setApplicantName(applicantName);
        return a;
    }

    private TeamIssueServiceImpl newService(List<TeamTask> tasks, List<TeamBug> bugs,
            List<TeamApproval> approvals, Map<String, List<TeamLabel>> labelMap) {
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        ITeamBugService bugService = mock(ITeamBugService.class);
        ITeamApprovalService approvalService = mock(ITeamApprovalService.class);
        ITeamTaskLabelService taskLabelService = mock(ITeamTaskLabelService.class);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(tasks);
        when(bugService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(bugs);
        when(approvalService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(approvals);
        when(taskLabelService.listLabelsByTaskIds(any())).thenReturn(labelMap);

        TeamIssueServiceImpl svc = new TeamIssueServiceImpl();
        ReflectionTestUtils.setField(svc, "teamTaskService", taskService);
        ReflectionTestUtils.setField(svc, "teamBugService", bugService);
        ReflectionTestUtils.setField(svc, "teamApprovalService", approvalService);
        ReflectionTestUtils.setField(svc, "taskLabelService", taskLabelService);
        return svc;
    }

    private TeamIssueServiceImpl sampleService() {
        TeamLabel label = new TeamLabel();
        ReflectionTestUtils.setField(label, "id", "l1");
        label.setName("紧急");
        label.setColor("#d73a49");
        Map<String, List<TeamLabel>> labelMap = new HashMap<>();
        labelMap.put("t1", Collections.singletonList(label));
        return newService(
                Arrays.asList(
                        task("t1", 12, "修复登录页", "doing", 1, time(30), "张三"),
                        task("t2", 13, "写周报", "done", 0, time(10), "李四")),
                Collections.singletonList(
                        bug("b1", 3, "提交按钮错位", "processing", time(20), "王五", null)),
                Collections.singletonList(
                        approval("a1", "OA-20261123-001", "周末加班申请", "pending", time(40), "赵六")),
                labelMap);
    }

    @Test
    void allKindsMergedSortedAndMapped() {
        TeamIssueServiceImpl svc = sampleService();
        IPage<TeamIssueRow> page = svc.issuePage("all", null, null, "all", 1, 10);

        assertEquals(4, page.getTotal(), "三源共 4 行");
        List<TeamIssueRow> rows = page.getRecords();
        assertEquals(4, rows.size());
        // create_time desc：approval(40) > task1(30) > bug(20) > task2(10)
        assertEquals("approval", rows.get(0).getKind());
        assertEquals("task", rows.get(1).getKind());
        assertEquals("bug", rows.get(2).getKind());
        assertEquals("task", rows.get(3).getKind());

        TeamIssueRow taskRow = rows.get(1);
        assertEquals("T-12", taskRow.getCode());
        assertEquals("open", taskRow.getState(), "doing → open");
        assertEquals("张三", taskRow.getAssigneeName());
        assertEquals(1, taskRow.getLabels().size());
        assertEquals("紧急", taskRow.getLabels().get(0).getName());
        assertTrue(taskRow.getBlocked(), "blocked=1 应透传");

        TeamIssueRow doneTask = rows.get(3);
        assertEquals("closed", doneTask.getState(), "done → closed");
        assertFalse(doneTask.getBlocked());

        TeamIssueRow bugRow = rows.get(2);
        assertEquals("B-3", bugRow.getCode());
        assertEquals("open", bugRow.getState(), "processing → open");
        assertEquals("王五", bugRow.getAssigneeName(), "处理人为空回退提交人");
        assertTrue(bugRow.getLabels().isEmpty());

        TeamIssueRow approvalRow = rows.get(0);
        assertEquals("OA-20261123-001", approvalRow.getCode(), "审批单号原样");
        assertEquals("open", approvalRow.getState(), "pending → open");
        assertEquals("赵六", approvalRow.getAssigneeName());
    }

    @Test
    void paginationIsInMemoryOverMergedSortedRows() {
        TeamIssueServiceImpl svc = sampleService();
        IPage<TeamIssueRow> page = svc.issuePage("all", null, null, "all", 2, 3);
        assertEquals(4, page.getTotal());
        assertEquals(1, page.getRecords().size(), "第二页只剩 1 行");
        assertEquals("T-13", page.getRecords().get(0).getCode(), "最旧的在第二页");
    }

    @Test
    void keywordMatchesTitleAndDisplayCode() {
        TeamIssueServiceImpl svc = sampleService();
        IPage<TeamIssueRow> byCode = svc.issuePage("all", "t-12", null, "all", 1, 10);
        assertEquals(1, byCode.getTotal(), "keyword 命中展示编号（大小写不敏感）");
        assertEquals("T-12", byCode.getRecords().get(0).getCode());

        IPage<TeamIssueRow> byTitle = svc.issuePage("all", "按钮", null, "all", 1, 10);
        assertEquals(1, byTitle.getTotal(), "keyword 命中 bug 描述");
        assertEquals("bug", byTitle.getRecords().get(0).getKind());

        IPage<TeamIssueRow> byOa = svc.issuePage("all", "OA-20261123", null, "all", 1, 10);
        assertEquals(1, byOa.getTotal(), "keyword 命中 OA 单号");
    }

    @Test
    void stateFilterPushesStatusConditionToEachDomain() {
        TeamIssueServiceImpl svc = sampleService();

        // state=closed → 各域 wrapper 须下发 NOT IN(openCodes)
        svc.issuePage("all", null, null, "closed", 1, 10);
        ITeamTaskService taskService = (ITeamTaskService) ReflectionTestUtils.getField(svc, "teamTaskService");
        ArgumentCaptor<LambdaQueryWrapper<TeamTask>> closedCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskService).list(closedCaptor.capture());
        String closedSegment = closedCaptor.getValue().getSqlSegment();
        assertTrue(closedSegment.contains("status") && closedSegment.contains("NOT IN"),
                "closed 过滤须下发 status NOT IN: " + closedSegment);

        // state=open → 各域 wrapper 须下发 IN(openCodes)
        TeamIssueServiceImpl svc2 = sampleService();
        svc2.issuePage("all", null, null, "open", 1, 10);
        ITeamTaskService taskService2 = (ITeamTaskService) ReflectionTestUtils.getField(svc2, "teamTaskService");
        ArgumentCaptor<LambdaQueryWrapper<TeamTask>> openCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskService2).list(openCaptor.capture());
        String openSegment = openCaptor.getValue().getSqlSegment();
        assertTrue(openSegment.contains("status") && openSegment.contains("IN"),
                "open 过滤须下发 status IN: " + openSegment);

        // state=all → 不下发状态条件
        TeamIssueServiceImpl svc3 = sampleService();
        svc3.issuePage("all", null, null, "all", 1, 10);
        ITeamTaskService taskService3 = (ITeamTaskService) ReflectionTestUtils.getField(svc3, "teamTaskService");
        ArgumentCaptor<LambdaQueryWrapper<TeamTask>> allCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskService3).list(allCaptor.capture());
        assertTrue(allCaptor.getValue().getSqlSegment() == null
                        || !allCaptor.getValue().getSqlSegment().contains("status"),
                "all 不下发状态条件: " + allCaptor.getValue().getSqlSegment());
    }

    @Test
    void assigneeIdRoutesToDomainAssigneeColumns() {
        TeamIssueServiceImpl verifySvc = sampleService();
        verifySvc.issuePage("all", null, "u-1", "all", 1, 10);

        ITeamTaskService taskService = (ITeamTaskService) ReflectionTestUtils.getField(verifySvc, "teamTaskService");
        ITeamBugService bugService = (ITeamBugService) ReflectionTestUtils.getField(verifySvc, "teamBugService");
        ITeamApprovalService approvalService = (ITeamApprovalService) ReflectionTestUtils.getField(verifySvc, "teamApprovalService");

        ArgumentCaptor<LambdaQueryWrapper<TeamTask>> taskCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskService).list(taskCaptor.capture());
        assertTrue(taskCaptor.getValue().getSqlSegment().contains("assignee_id"),
                "task 域按 assignee_id 过滤: " + taskCaptor.getValue().getSqlSegment());

        ArgumentCaptor<LambdaQueryWrapper<TeamBug>> bugCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bugService).list(bugCaptor.capture());
        assertTrue(bugCaptor.getValue().getSqlSegment().contains("reporter_id"),
                "bug 域按 reporter_id 过滤: " + bugCaptor.getValue().getSqlSegment());

        ArgumentCaptor<LambdaQueryWrapper<TeamApproval>> approvalCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(approvalService).list(approvalCaptor.capture());
        assertTrue(approvalCaptor.getValue().getSqlSegment().contains("applicant_id"),
                "approval 域按 applicant_id 过滤: " + approvalCaptor.getValue().getSqlSegment());
    }

    @Test
    void kindFilterSkipsOtherSources() {
        TeamIssueServiceImpl svc = sampleService();
        IPage<TeamIssueRow> page = svc.issuePage("bug", null, null, "all", 1, 10);
        assertEquals(1, page.getTotal());
        assertEquals("bug", page.getRecords().get(0).getKind());

        ITeamTaskService taskService = (ITeamTaskService) ReflectionTestUtils.getField(svc, "teamTaskService");
        ITeamApprovalService approvalService = (ITeamApprovalService) ReflectionTestUtils.getField(svc, "teamApprovalService");
        verify(taskService, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(approvalService, never()).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void nullCodeWhenSequenceMissing() {
        TeamIssueServiceImpl svc = newService(
                Collections.singletonList(task("t9", null, "无序号任务", "todo", 0, time(1), null)),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
        IPage<TeamIssueRow> page = svc.issuePage("task", null, null, "all", 1, 10);
        assertEquals(1, page.getTotal());
        assertNull(page.getRecords().get(0).getCode(), "taskNo 缺失时 code 为 null（不编造编号）");
    }
}
