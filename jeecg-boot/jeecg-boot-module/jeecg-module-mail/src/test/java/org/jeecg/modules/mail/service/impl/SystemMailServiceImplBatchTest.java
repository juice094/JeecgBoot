package org.jeecg.modules.mail.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.dto.MailBatchSendItemResult;
import org.jeecg.modules.mail.dto.MailSendSingleReqDTO;
import org.junit.jupiter.api.Test;

/**
 * @Description: SystemMailServiceImpl 批量发送守卫单测（零 Spring，new 实例直测守卫逻辑）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（接口文档 §3.3）：批量 ≤500 且非空；单条失败不影响其他条。
 */
class SystemMailServiceImplBatchTest {

    private final SystemMailServiceImpl service = new SystemMailServiceImpl();

    @Test
    void sendBatch_nullOrEmpty_rejected() {
        assertThrows(JeecgBootException.class, () -> service.sendBatch(null));
        assertThrows(JeecgBootException.class, () -> service.sendBatch(Collections.emptyList()));
    }

    @Test
    void sendBatch_overLimit_rejected() {
        List<MailSendSingleReqDTO> requests = new ArrayList<>(501);
        for (int i = 0; i < 501; i++) {
            MailSendSingleReqDTO req = new MailSendSingleReqDTO();
            req.setTemplateCode("T");
            requests.add(req);
        }
        JeecgBootException ex = assertThrows(JeecgBootException.class, () -> service.sendBatch(requests));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void sendBatch_singleItemFailure_capturedNotPropagated() {
        // 模板编码为空 → sendSingleMail 在校验期即抛错，不得触碰数据库协作件
        MailSendSingleReqDTO bad = new MailSendSingleReqDTO();
        bad.setTemplateCode(" ");
        MailSendSingleReqDTO badNull = null;
        List<MailBatchSendItemResult> results = service.sendBatch(java.util.Arrays.asList(bad, badNull));
        assertEquals(2, results.size());
        assertEquals(0, results.get(0).getIndex());
        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(0).getErrorMessage().contains("模板编码不能为空"));
        assertEquals(1, results.get(1).getIndex());
        assertFalse(results.get(1).isSuccess());
    }
}
