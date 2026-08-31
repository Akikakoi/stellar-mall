package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.annotation.RequireRole;
import com.stellar.service.ChatBiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端 AI 智能查数（ChatBI）：自然语言 → 安全 SELECT SQL → 图表 + 回答。
 */
@Slf4j
@RestController
@RequestMapping("/admin/chatbi")
@RequiredArgsConstructor
@Api(tags = "管理端：AI 智能查数")
public class AdminChatBiController {

    private final ChatBiService chatBiService;

@RequireRole({1, 2})
    @PostMapping("/query")
    @ApiOperation("自然语言查数（LLM 生成 SQL 并执行，耗时较长，前端需放宽超时）")
    public Result<Map<String, Object>> query(@RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        if (!StringUtils.hasText(question)) {
            return Result.error("请输入您的问题");
        }
        if (question.trim().length() > 500) {
            return Result.error("问题过长，请控制在 500 字以内");
        }
        try {
            return Result.success(chatBiService.query(question));
        } catch (Exception e) {
            log.error("[ChatBI] 查询失败: question={}, err={}", question, e.getMessage());
            return Result.error(e.getMessage() == null ? "查询失败，请稍后重试" : e.getMessage());
        }
    }
}
