package com.lanou.wordsync.realtime;

import com.lanou.wordsync.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 查询某文档当前在线用户。
 */
@RestController
@RequestMapping("/word-sync/api/documents")
public class OnlineController {

    private final OnlineUserService onlineUserService;

    public OnlineController(OnlineUserService onlineUserService) {
        this.onlineUserService = onlineUserService;
    }

    @GetMapping("/{docId}/online")
    public Result<List<String>> online(@PathVariable Long docId) {
        return Result.ok(onlineUserService.list(docId));
    }
}
