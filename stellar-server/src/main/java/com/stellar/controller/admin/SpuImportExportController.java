package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.service.SpuImportExportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 商品批量导入/导出
 */
@RestController
@RequestMapping("/admin/spu/import-export")
@RequiredArgsConstructor
@Api(tags = "管理端：商品导入导出")
public class SpuImportExportController {

    private final SpuImportExportService importExportService;

    @GetMapping("/export")
    @ApiOperation("导出全部商品为 Excel")
    public ResponseEntity<byte[]> exportAll() throws UnsupportedEncodingException {
        byte[] data = importExportService.exportAll();
        String filename = URLEncoder.encode("商品数据导出.xlsx", StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(data);
    }

    @GetMapping("/template")
    @ApiOperation("下载导入模板")
    public ResponseEntity<byte[]> downloadTemplate() throws UnsupportedEncodingException {
        byte[] data = importExportService.generateTemplate();
        String filename = URLEncoder.encode("商品导入模板.xlsx", StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(data);
    }

    @PostMapping("/import")
    @ApiOperation("批量导入商品")
    public Result<Map<String, Object>> importSpu(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("上传文件为空");
        }
        try {
            Map<String, Object> summary = importExportService.importFromExcel(file);
            return Result.success(summary);
        } catch (Exception e) {
            return Result.error("导入失败：" + e.getMessage());
        }
    }
}
