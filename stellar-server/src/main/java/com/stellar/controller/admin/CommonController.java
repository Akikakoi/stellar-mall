package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.annotation.RequireRole;
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

    /** 允许的图片扩展名（白名单，排除 svg——可内嵌脚本触发 XSS；排除 html/exe 等任意文件） */
    private static final Set<String> ALLOWED_EXT = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".ico")));

    /** 允许的业务模块（防 module 路径注入：module 会拼进 OSS key） */
    private static final Set<String> ALLOWED_MODULES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "spu", "category", "banner", "employee", "user", "kb", "review", "after-sale")));

    /** 单张图片大小上限：5MB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    /** 单次上传数量上限 */
    private static final int MAX_FILES = 20;

@RequireRole({1, 2, 3, 4})
    @PostMapping("/upload")
    @ApiOperation("上传图片（支持单张/多张），返回 OSS URL 列表")
    public Result<List<String>> upload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "module", defaultValue = "spu") String module) {
        if (files == null || files.length == 0) {
            return Result.error("请选择至少一张图片");
        }
        if (files.length > MAX_FILES) {
            return Result.error("单次最多上传 " + MAX_FILES + " 张图片");
        }
        if (!ALLOWED_MODULES.contains(module)) {
            return Result.error("非法的业务模块: " + module);
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String name = file.getOriginalFilename();
            String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            String ext = dot >= 0 ? lower.substring(dot) : "";
            if (!ALLOWED_EXT.contains(ext)) {
                return Result.error("仅支持图片格式: " + String.join("/", ALLOWED_EXT) + "（当前: " + name + "）");
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                return Result.error("单张图片不能超过 5MB: " + name);
            }
            try {
                String url = aliOssUtil.upload(file, module);
                urls.add(url);
            } catch (Exception e) {
                log.error("[CommonController] upload failed for file={}", name, e);
                return Result.error("上传失败: " + name + " - " + e.getMessage());
            }
        }
        return Result.success(urls);
    }
}
