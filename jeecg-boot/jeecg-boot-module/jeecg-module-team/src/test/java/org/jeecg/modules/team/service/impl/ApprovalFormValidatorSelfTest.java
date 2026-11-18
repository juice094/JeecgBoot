package org.jeecg.modules.team.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.jeecg.common.exception.JeecgBootException;

/**
 * @Description: ApprovalFormValidator 纯 Java 自测（无 Spring / 无 JUnit）
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 */
public final class ApprovalFormValidatorSelfTest {

    private static int assertions = 0;

    public static void main(String[] args) {
        noSchema_passthrough();
        required_missing_rejected();
        optional_missing_ok();
        number_belowMin_rejected();
        number_aboveMax_rejected();
        number_nonNumeric_rejected();
        select_invalidOption_rejected();
        multiselect_invalidOption_rejected();
        date_badFormat_rejected();
        extraKeys_dropped();
        amount_extracted();
        System.out.println("ApprovalFormValidatorSelfTest PASSED, assertions=" + assertions);
    }

    private static final String SCHEMA = "["
            + "{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"number\",\"required\":true,\"min\":0,\"max\":100000},"
            + "{\"key\":\"expenseType\",\"label\":\"费用类型\",\"type\":\"select\",\"required\":true,\"options\":[\"差旅\",\"招待\",\"办公\"]},"
            + "{\"key\":\"invoiceCount\",\"label\":\"发票张数\",\"type\":\"number\"},"
            + "{\"key\":\"travelDate\",\"label\":\"出发日期\",\"type\":\"date\"},"
            + "{\"key\":\"tags\",\"label\":\"标签\",\"type\":\"multiselect\",\"options\":[\"A\",\"B\"]}"
            + "]";

    private static void noSchema_passthrough() {
        ApprovalFormValidator.Result r = ApprovalFormValidator.validate(null, "{\"foo\":\"bar\",\"amount\":12.5}");
        check(r.getAmount().compareTo(new BigDecimal("12.5")) == 0, "无 schema 时应直接提取 amount");
        check(r.getCleaned().containsKey("foo"), "无 schema 时应透传字段");
    }

    private static void required_missing_rejected() {
        expectFail("{\"expenseType\":\"差旅\"}", "金额");
    }

    private static void optional_missing_ok() {
        ApprovalFormValidator.Result r = ApprovalFormValidator.validate(SCHEMA,
                "{\"amount\":100,\"expenseType\":\"差旅\"}");
        check(!r.getCleaned().containsKey("invoiceCount"), "非必填缺失时不应写入");
    }

    private static void number_belowMin_rejected() {
        expectFail("{\"amount\":-1,\"expenseType\":\"差旅\"}", "不能小于");
    }

    private static void number_aboveMax_rejected() {
        expectFail("{\"amount\":100001,\"expenseType\":\"差旅\"}", "不能大于");
    }

    private static void number_nonNumeric_rejected() {
        expectFail("{\"amount\":\"abc\",\"expenseType\":\"差旅\"}", "应为数字");
    }

    private static void select_invalidOption_rejected() {
        expectFail("{\"amount\":1,\"expenseType\":\"其他\"}", "取值非法");
    }

    private static void multiselect_invalidOption_rejected() {
        expectFail("{\"amount\":1,\"expenseType\":\"差旅\",\"tags\":[\"A\",\"C\"]}", "非法取值");
    }

    private static void date_badFormat_rejected() {
        expectFail("{\"amount\":1,\"expenseType\":\"差旅\",\"travelDate\":\"11/20\"}", "日期格式");
    }

    private static void extraKeys_dropped() {
        ApprovalFormValidator.Result r = ApprovalFormValidator.validate(SCHEMA,
                "{\"amount\":1,\"expenseType\":\"差旅\",\"evil\":\"x\"}");
        check(!r.getCleaned().containsKey("evil"), "schema 外的键应被丢弃");
    }

    private static void amount_extracted() {
        ApprovalFormValidator.Result r = ApprovalFormValidator.validate(SCHEMA,
                "{\"amount\":\"3800.50\",\"expenseType\":\"差旅\",\"tags\":[\"A\"]}");
        check(r.getAmount().compareTo(new BigDecimal("3800.50")) == 0, "amount 应被提取为 BigDecimal");
        check(r.toJson().contains("3800.50"), "序列化应保留金额");
    }

    private static void expectFail(String formData, String expectFragment) {
        try {
            ApprovalFormValidator.validate(SCHEMA, formData);
            throw new AssertionError("应抛异常但未抛: " + formData);
        } catch (JeecgBootException e) {
            check(e.getMessage().contains(expectFragment),
                    "异常文案应含「" + expectFragment + "」，实际: " + e.getMessage());
        }
    }

    private static void check(boolean cond, String msg) {
        assertions++;
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    private ApprovalFormValidatorSelfTest() {
    }
}
