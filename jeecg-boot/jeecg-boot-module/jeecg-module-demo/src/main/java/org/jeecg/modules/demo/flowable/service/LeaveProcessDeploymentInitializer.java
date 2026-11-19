package org.jeecg.modules.demo.flowable.service;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 请假流程定义的兜底部署器。
 * <p>
 * Flowable starter 的 classpath*:processes/ 自动部署在可执行 fat jar 模式下
 * 无法扫到 BOOT-INF/lib 内嵌 jar 里的 bpmn（Spring Boot 嵌套 jar 不支持
 * classpath* 通配扫描），改为启动时用 RepositoryService 按 classpath 单资源部署，
 * 幂等：流程定义已存在则跳过。
 * ponytail: 尖刺最小实现——按 processDefinitionKey 判存在；多流程定义时升级路径为
 * 遍历 processes/ 目录逐个 addInputStream 部署。
 */
@Slf4j
@Component
@Order
public class LeaveProcessDeploymentInitializer implements ApplicationRunner {

    /** 流程定义 key，与 processes/leave-process.bpmn20.xml 中的 process id 一致 */
    private static final String PROCESS_KEY_LEAVE = "leaveProcess";
    /** 部署的 bpmn 资源路径（classpath 单资源读取，fat jar 内嵌 jar 亦可用） */
    private static final String BPMN_CLASSPATH_RESOURCE = "processes/leave-process.bpmn20.xml";
    /** 部署显示名 */
    private static final String DEPLOYMENT_NAME = "leave-process-init";

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void run(ApplicationArguments args) {
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_KEY_LEAVE)
                .latestVersion()
                .singleResult();
        if (definition != null) {
            log.info("leave process definition already deployed, key={}, version={}",
                    PROCESS_KEY_LEAVE, definition.getVersion());
            return;
        }
        repositoryService.createDeployment()
                .name(DEPLOYMENT_NAME)
                .addClasspathResource(BPMN_CLASSPATH_RESOURCE)
                .deploy();
        log.info("leave process definition deployed from classpath, resource={}", BPMN_CLASSPATH_RESOURCE);
    }
}
