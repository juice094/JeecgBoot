package com.lanou.wordsync.document.mapper;

import com.lanou.wordsync.document.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档 Mapper，SQL 在 resources/mapper/DocumentMapper.xml。
 */
@Mapper
@DS("wordsync")
public interface DocumentMapper {

    List<Document> selectPage(@Param("keyword") String keyword,
                              @Param("ownerId") Long ownerId,
                              @Param("offset") int offset,
                              @Param("size") int size);

    long count(@Param("keyword") String keyword,
               @Param("ownerId") Long ownerId);

    List<Document> selectDeletedPage(@Param("offset") int offset,
                                     @Param("size") int size);

    long countDeleted();

    Document selectById(@Param("documentId") Long documentId);

    int insert(Document document);

    int updateStatus(@Param("documentId") Long documentId,
                     @Param("status") String status);

    int restoreStatus(@Param("documentId") Long documentId);
}
