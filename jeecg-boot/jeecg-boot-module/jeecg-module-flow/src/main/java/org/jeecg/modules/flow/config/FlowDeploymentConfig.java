package org.jeecg.modules.flow.config;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 显式部署流程定义（请假 W1 + 报销 W2）。
 * 不依赖 starter 的 classpath* 自动扫描（在 Spring Boot 嵌套 jar 打包下未生效），
 * 改用 addClasspathResource 编程式部署，单资源读取在嵌套 jar 下稳定可用。
 * 幂等：流程定义 key 已存在（取最新版本）则跳过，不重复建部署。
 */
@Slf4j
@Configuration
public class FlowDeploymentConfig {

    /** 请假流程定义 key，与 processes/leave-process.bpmn20.xml 中的 process id 一致 */
    private static final String PROCESS_KEY_LEAVE = "leaveProcess";
    /** 报销流程定义 key，与 processes/expense-process.bpmn20.xml 中的 process id 一致 */
    private static final String PROCESS_KEY_EXPENSE = "expenseProcess";
    /** 请假流程定义资源路径（flow 模块 jar 内） */
    private static final String PROCESS_RESOURCE_PATH_LEAVE = "processes/leave-process.bpmn20.xml";
    /** 报销流程定义资源路径（flow 模块 jar 内） */
    private static final String PROCESS_RESOURCE_PATH_EXPENSE = "processes/expense-process.bpmn20.xml";
    /** 请假流程部署名 */
    private static final String DEPLOYMENT_NAME_LEAVE = "flow-leave-process-w1";
    /** 报销流程部署名 */
    private static final String DEPLOYMENT_NAME_EXPENSE = "flow-expense-process-w2";

    @Bean
    public CommandLineRunner flowDeploymentRunner(RepositoryService repositoryService) {
        return args -> {
            deployIfAbsent(repositoryService, PROCESS_KEY_LEAVE, PROCESS_RESOURCE_PATH_LEAVE, DEPLOYMENT_NAME_LEAVE);
            deployIfAbsent(repositoryService, PROCESS_KEY_EXPENSE, PROCESS_RESOURCE_PATH_EXPENSE, DEPLOYMENT_NAME_EXPENSE);
        };
    }

    private void deployIfAbsent(RepositoryService repositoryService, String processKey, String resourcePath, String deploymentName) {
        ProcessDefinition existing = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        if (existing != null) {
            log.info("process definition already deployed, key={}, version={}", processKey, existing.getVersion());
            return;
        }
        repositoryService.createDeployment()
                .name(deploymentName)
                .addClasspathResource(resourcePath)
                .deploy();
        log.info("process definition deployed, key={}, resource={}", processKey, resourcePath);
    }
}
