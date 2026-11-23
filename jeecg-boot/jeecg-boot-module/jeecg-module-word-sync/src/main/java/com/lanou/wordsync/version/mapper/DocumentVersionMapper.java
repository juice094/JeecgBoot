package com.lanou.wordsync.version.mapper;

import com.lanou.wordsync.version.entity.DocumentVersion;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档版本 Mapper，SQL 在 resources/mapper/DocumentVersionMapper.xml。
 */
@Mapper
@DS("wordsync")
public interface DocumentVersionMapper {

    int insert(DocumentVersion version);

    List<DocumentVersion> selectByDocId(@Param("docId") Long docId);

    DocumentVersion selectByDocIdAndVersion(@Param("docId") Long docId,
                                            @Param("versionNo") Integer versionNo);
}
