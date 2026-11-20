package org.jeecg.modules.team.service.impl;

import java.util.List;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamApprovalStep;

/**
 * 纯 Java 自测（无 JUnit 依赖前提下的最小可运行检查）：
 * TemplateStepParser 模板步骤链解析——正常解析、非法 JSON、空链、缺审批人。
 * 依赖 Jackson（classpath 由 mvn dependency:build-classpath 生成）。
 *
 * 运行方式：
 *   java -ea -cp classes;test-classes;<dep-cp> org.jeecg.modules.team.service.impl.TemplateStepParserSelfTest
 */
public final class TemplateStepParserSelfTest {

    private static int checked = 0;

    public static void main(String[] args) {
        // 正常解析
        List<TeamApprovalStep> steps = TemplateStepParser.parse("leave",
                "[{\"approverName\":\"贺致远\",\"approverRole\":\"部门主管\"},"
                        + "{\"approverName\":\"黄茜茜\",\"approverRole\":\"副总经理\"}]");
        assertTrue(steps.size() == 2, "应解析 2 步，实际 " + steps.size());
        assertTrue("贺致远".equals(steps.get(0).getApproverName()), "第1步审批人错误");
        assertTrue("副总经理".equals(steps.get(1).getApproverRole()), "第2步角色错误");

        // 非法 JSON
        assertThrow("leave", "[{broken", JeecgBootException.class);
        // 空链 / 空文本
        assertThrow("leave", "[]", JeecgBootException.class);
        assertThrow("leave", "", JeecgBootException.class);
        assertThrow("leave", null, JeecgBootException.class);
        // 缺审批人（第 2 步）
        assertThrow("leave",
                "[{\"approverName\":\"贺致远\"},{\"approverName\":\"\"}]", JeecgBootException.class);
        // null 元素
        assertThrow("leave", "[null]", JeecgBootException.class);

        System.out.println("TemplateStepParserSelfTest PASSED, assertions=" + checked);
    }

    private static void assertThrow(String code, String json, Class<? extends Exception> type) {
        checked++;
        try {
            TemplateStepParser.parse(code, json);
        } catch (JeecgBootException e) {
            return;
        }
        throw new AssertionError("应抛 JeecgBootException: json=" + json);
    }

    private static void assertTrue(boolean condition, String message) {
        checked++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private TemplateStepParserSelfTest() {
    }
}
