package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * 通用接口：文件上传等。
 */
@Slf4j
@RestController
@RequestMapping("/admin/common")
@RequiredArgsConstructor
@Api(tags = "管理端：通用接口")
public class CommonController {

    private final AliOssUtil aliOssUtil;

    @PostMapping("/upload")
    @ApiOperation("上传图片（支持单张/多张），返回 OSS URL 列表")
    public Result<List<String>> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "module", defaultValue = "spu") String module) {
        if (files == null || files.length == 0) {
            return Result.error("请选择至少一张图片");
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                String url = aliOssUtil.upload(file, module);
                urls.add(url);
            } catch (Exception e) {
                log.error("[CommonController] upload failed for file={}", file.getOriginalFilename(), e);
                return Result.error("上传失败: " + file.getOriginalFilename() + " - " + e.getMessage());
            }
        }
        return Result.success(urls);
    }
}
