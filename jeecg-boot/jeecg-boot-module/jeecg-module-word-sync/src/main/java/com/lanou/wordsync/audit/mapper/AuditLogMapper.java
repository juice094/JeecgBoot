package com.lanou.wordsync.audit.mapper;

import com.lanou.wordsync.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审计日志 Mapper，SQL 写在 resources/mapper/AuditLogMapper.xml。
 */
@Mapper
@DS("wordsync")
public interface AuditLogMapper {

    int insert(AuditLog log);

    List<AuditLog> selectByDocId(@Param("docId") Long docId,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    long countByDocId(@Param("docId") Long docId);
}
