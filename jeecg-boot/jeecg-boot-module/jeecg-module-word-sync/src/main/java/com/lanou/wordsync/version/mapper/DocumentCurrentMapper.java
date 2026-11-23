package com.lanou.wordsync.version.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Param;

/**
 * 更新文档当前版本（只更新 tb_document 的 current_version/storage_key）。
 *
 * 说明：版本模块不依赖文档模块，避免和 document -> version 形成循环依赖。
 */
@Mapper
@DS("wordsync")
public interface DocumentCurrentMapper {

    int updateCurrent(@Param("docId") Long docId,
                      @Param("versionNo") Integer versionNo,
                      @Param("storageKey") String storageKey);

    /** 查询文档当前版本号，用于 ETag / If-Match 校验 */
    Integer selectCurrentVersion(@Param("docId") Long docId);
}
