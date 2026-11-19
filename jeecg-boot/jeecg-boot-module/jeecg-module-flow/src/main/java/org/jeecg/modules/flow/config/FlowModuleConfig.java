package org.jeecg.modules.flow.config;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 流程域模块配置锚点：Flowable 引擎参数外置（W1-T2）。
 *
 * 认知模型（立项草案 docs/27）：本模块是"流程域服务"，与 team（单据域）平级；
 * 审批链迁移后 team 只管单据状态，流转驱动由 Flowable 引擎承担。
 *
 * 引擎参数取舍（逐项说明）：
 * 1. HistoryLevel=AUDIT——显式声明而非吃默认：AUDIT 记录 task 级办理轨迹
 *    （办理人/完成时间/审批意见落点），是 W2 审批链审计的最小够用档；
 *    NONE/INSTANCE 省 act_hi_* 表但丢掉办理轨迹，不满足审计回溯；FULL 额外记录
 *    全部变量快照，表膨胀快而 W1 无消费方，不开。若 W2 要变量级回溯再升 FULL。
 * 2. 异步执行器 dev 默认开——timer 事件、异步延续（async=true）依赖 job executor
 *    才会被触发；dev 关闭会出现"流程莫名卡住"的假象，排查成本高，故默认开。
 *    多实例部署时需另行评估异步执行器的集群锁与分区策略，W1 单体不涉及。
 * 3. schema 策略保持 starter 默认（引擎自管）——不写 databaseSchemaUpdate 即锁定
 *    默认行为：启动时按引擎版本自检并增量维护 act_* 表。已拍决策：不导出 DDL
 *    进 flyway（引擎升级自带 schema 脚本，flyway 重复接管只会互相踩），
 *    application-dev.yml 在役段只读，故引擎参数全部收敛在本类。
 */
@Configuration
public class FlowModuleConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowEngineConfigurer() {
        return configuration -> {
            configuration.setHistoryLevel(HistoryLevel.AUDIT);
            configuration.setAsyncExecutorActivate(true);
        };
    }
}
