package com.stellar.controller.user;

import com.stellar.result.Result;
import com.stellar.service.ReviewCommentService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user/review")
@RequiredArgsConstructor
@Api(tags = "C端：评价评论")
public class ReviewCommentController {

    private final ReviewCommentService commentService;

    @GetMapping("/{reviewId}/comments")
    @ApiOperation("获取某条评价的所有评论")
    public Result<Object> listComments(@PathVariable Long reviewId) {
        return Result.success(commentService.listByReviewId(reviewId));
    }

    @PostMapping("/{reviewId}/comment")
    @ApiOperation("对评价发表评论")
    public Result<String> comment(@PathVariable Long reviewId,
                                  @RequestBody Map<String, String> body) {
        commentService.comment(reviewId, body.get("content"));
        return Result.success();
    }
}
