package com.stellar.controller.user;

import com.stellar.context.BaseContext;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.UserMessageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/message")
@RequiredArgsConstructor
@Api(tags = "C端：用户消息")
public class UserMessageController {

    private final UserMessageService userMessageService;

    @GetMapping("/list")
    @ApiOperation("分页查询当前用户的消息")
    public Result<PageResult> list(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(userMessageService.pageByUserId(userId, page, pageSize));
    }

    @GetMapping("/unread-count")
    @ApiOperation("获取当前用户未读消息数")
    public Result<Map<String, Long>> unreadCount() {
        Long userId = BaseContext.getCurrentId();
        long count = userMessageService.getUnreadCount(userId);
        return Result.success(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    @ApiOperation("标记消息为已读")
    public Result<Void> markAsRead(@PathVariable Long id) {
        userMessageService.markAsRead(BaseContext.getCurrentId(), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    @ApiOperation("全部标记已读")
    public Result<Void> markAllAsRead() {
        Long userId = BaseContext.getCurrentId();
        userMessageService.markAllAsRead(userId);
        return Result.success();
    }
}
