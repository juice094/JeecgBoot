package com.lanou.wordsync;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 将 word-sync 接入 JeecgBoot 单体应用：
 * - 开启 @Scheduled（RealtimePublisher 的在线 TTL 续期）；
 * - 开启 Kafka 监听（word.sync.events / word.audit.events）。
 *
 * Mapper 扫描统一由 Jeecg 的 MybatisPlusSaasConfig 负责，
 * 该配置已加入 com.lanou.wordsync.**.mapper*，不要在这里重复 @MapperScan，
 * 否则会覆盖 Jeecg 原有的 MapperScannerConfigurer，导致 team 模块 Mapper 缺失。
 */
@Configuration
@EnableKafka
@EnableScheduling
public class WordSyncModuleConfig {
}
