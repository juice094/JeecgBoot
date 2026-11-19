package org.jeecg.modules.flow.config;

import org.springframework.context.annotation.Configuration;

/**
 * 流程域模块配置锚点（骨架，W1 由功能线填充）。
 *
 * 认知模型（立项草案 docs/27）：
 * - 本模块是"流程域服务"，与 team（单据域）平级；审批链迁移后 team 只管单据状态，
 *   流转驱动由 Flowable 引擎承担。
 * - 身份底座唯一：流程办理人/候选组直接引用 sys_user/sys_role（agent 注册为
 *   sys_user 特殊类型后可作为 assignee/candidate，见治理基座设计稿 §3.3 v1.1）。
 * - 高敏操作门（AGENT_CONFIRM_REQUIRED）计划以 userTask + JavaDelegate 实现（W4）。
 *
 * W1 待办（功能线承接，勿在本类提前实现）：
 * 1. flowable 引擎参数外置（数据源沿用平台 master，schema 管理决策见 docs/27 §4）；
 * 2. BPMN 部署方式（classpath processes/ 目录 vs 流程定义表）；
 * 3. 与 Shiro 匿名/鉴权路径的边界（/flow/** 端点的权限码口径 team:flow:*）。
 */
@Configuration
public class FlowModuleConfig {
    // W1: 引擎配置 Bean（HistoryLevel、异步执行器开关、schema 策略）在此声明。
}
