package org.jeecg.modules.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式部署请假流程定义。
 * 不依赖 starter 的 classpath* 自动扫描（在 Spring Boot 嵌套 jar 打包下未生效），
 * 改用 addClasspathResource 编程式部署，单资源读取在嵌套 jar 下稳定可用。
 * 幂等：流程定义已存在则跳过。
 */
@Slf4j
@Configuration
public class FlowDeploymentConfig {

    /** 流程定义 key，与 processes/leave-process.bpmn20.xml 中的 process id 一致 */
    private static final String PROCESS_KEY_LEAVE = "leaveProcess";
    /** 流程定义资源路径（flow 模块 jar 内） */
    private static final String PROCESS_RESOURCE_PATH = "processes/leave-process.bpmn20.xml";
    /** 部署名（幂等判断依据之一） */
    private static final String DEPLOYMENT_NAME = "flow-leave-process-w1";

    @Bean
    public CommandLineRunner flowDeploymentRunner(RepositoryService repositoryService) {
        return args -> {
            ProcessDefinition existing = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(PROCESS_KEY_LEAVE)
                    .latestVersion()
                    .singleResult();
            if (existing != null) {
                log.info("leave process definition already deployed, key={}, version={}", PROCESS_KEY_LEAVE, existing.getVersion());
                return;
            }
            repositoryService.createDeployment()
                    .name(DEPLOYMENT_NAME)
                    .addClasspathResource(PROCESS_RESOURCE_PATH)
                    .deploy();
            log.info("leave process definition deployed, resource={}", PROCESS_RESOURCE_PATH);
        };
    }
}
