package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.annotation.RequireRole;
import com.stellar.service.DataExportService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin/export")
@RequiredArgsConstructor
@Api(tags = "管理端：数据导出")
public class DataExportController {

    private final DataExportService dataExportService;

@RequireRole({1, 2})
    @GetMapping("/orders")
    @ApiOperation("导出订单数据 Excel")
    public ResponseEntity<byte[]> exportOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) throws UnsupportedEncodingException {
        byte[] data = dataExportService.exportOrders(status, startTime, endTime);
        return buildResponse(data, "订单数据导出.xlsx");
    }

@RequireRole({1, 2})
    @GetMapping("/users")
    @ApiOperation("导出用户数据 Excel")
    public ResponseEntity<byte[]> exportUsers() throws UnsupportedEncodingException {
        byte[] data = dataExportService.exportUsers();
        return buildResponse(data, "用户数据导出.xlsx");
    }

@RequireRole({1, 4})
    @GetMapping("/finance")
    @ApiOperation("导出财务报表 Excel")
    public ResponseEntity<byte[]> exportFinance(
            @RequestParam(required = false) String year) throws UnsupportedEncodingException {
        byte[] data = dataExportService.exportFinanceReport(year);
        return buildResponse(data, "财务报表.xlsx");
    }

    private ResponseEntity<byte[]> buildResponse(byte[] data, String filename) throws UnsupportedEncodingException {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(data);
    }
}
