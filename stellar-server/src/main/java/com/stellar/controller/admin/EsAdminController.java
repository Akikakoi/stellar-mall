package com.stellar.controller.admin;

import com.stellar.elasticsearch.sync.SpuEsSyncService;
import com.stellar.annotation.RequireRole;
import com.stellar.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ES 索引管理（全量重建等）。
 */
@RestController
@RequestMapping("/admin/es")
@RequiredArgsConstructor
@Api(tags = "管理端：ES 索引管理")
public class EsAdminController {

    private final SpuEsSyncService spuEsSyncService;

@RequireRole({1, 2})
    @PostMapping("/rebuild")
    @ApiOperation("全量重建 SPU 搜索索引（将 MySQL 所有商品同步到 ES）")
    public Result<Long> rebuild() {
        long count = spuEsSyncService.rebuildAll();
        return Result.success(count);
    }
}
