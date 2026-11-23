package com.lanou.wordsync.onlyoffice;

import com.lanou.wordsync.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前端获取 OnlyOffice 初始化配置、触发保存的接口。
 */
@RestController
@RequestMapping("/word-sync/api/documents")
public class EditorController {

    private final EditorConfigService editorConfigService;
    private final OnlyOfficeCommandService commandService;

    public EditorController(EditorConfigService editorConfigService,
                            OnlyOfficeCommandService commandService) {
        this.editorConfigService = editorConfigService;
        this.commandService = commandService;
    }

    @GetMapping("/{docId}/editor-config")
    public Result<Map<String, Object>> editorConfig(@PathVariable Long docId,
                                                    @RequestParam(required = false) Long userId,
                                                    @RequestParam(required = false) String userName,
                                                    @RequestParam(required = false) String mode) {
        return Result.ok(editorConfigService.build(docId, userId, userName, mode));
    }

    /**
     * 前端“保存新版本”按钮：后端向 OnlyOffice 发 forcesave 命令，
     * OnlyOffice 会回调 status=6，随后由回调生成版本。
     */
    @PostMapping("/{docId}/force-save")
    public Result<Map<String, Object>> forceSave(@PathVariable Long docId) {
        return Result.ok(commandService.forceSave(docId));
    }
}
