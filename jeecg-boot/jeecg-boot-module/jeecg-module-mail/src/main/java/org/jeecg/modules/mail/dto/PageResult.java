package org.jeecg.modules.mail.dto;

import java.util.Collections;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 契约层-分页结果（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * 契约层不暴露 MyBatis-Plus 的 IPage，避免能力层/Agent 被持久层类型污染。
 */
@Data
@Schema(description = "契约层-分页结果")
public class PageResult<T> {

    @Schema(description = "当前页记录")
    private List<T> records = Collections.emptyList();

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "当前页码")
    private long current;

    @Schema(description = "每页条数")
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> r = new PageResult<>();
        r.records = records == null ? Collections.emptyList() : records;
        r.total = total;
        r.current = current;
        r.size = size;
        return r;
    }
}
