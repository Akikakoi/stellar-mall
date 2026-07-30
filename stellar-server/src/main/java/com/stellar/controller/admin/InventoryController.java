package com.stellar.controller.admin;

import com.stellar.result.PageResult;
import com.stellar.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@Api(tags = "管理端：库存管理")
public class InventoryController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    @ApiOperation("SKU 库存分页（支持 lowStock 参数过滤低库存）")
    public Result<PageResult> page(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer pageSize,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) Integer lowStock) {
        int offset = (page - 1) * pageSize;
        StringBuilder sql = new StringBuilder("SELECT * FROM stellar_sku WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM stellar_sku WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            sql.append(" AND name LIKE ?");
            countSql.append(" AND name LIKE ?");
            params.add("%" + name + "%");
        }
        if (lowStock != null && lowStock == 1) {
            sql.append(" AND stock <= warn_stock AND status = 1");
            countSql.append(" AND stock <= warn_stock AND status = 1");
        }
        sql.append(" ORDER BY stock ASC, id ASC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, params.subList(0, params.size() - 2).toArray());
        return Result.success(new PageResult(total == null ? 0L : total, list == null ? new ArrayList<>() : list));
    }

    @PutMapping("/stock")
    @ApiOperation("调整库存")
    public Result<String> updateStock(@RequestBody Map<String, Object> body) {
        Long skuId = Long.valueOf(body.get("skuId").toString());
        Integer delta = Integer.valueOf(body.get("delta").toString());
        Integer warnStock = body.get("warnStock") != null ? Integer.valueOf(body.get("warnStock").toString()) : null;
        if (delta != 0) {
            jdbcTemplate.update("UPDATE stellar_sku SET stock = GREATEST(0, stock + ?), update_time = NOW() WHERE id = ?", delta, skuId);
        }
        if (warnStock != null) {
            jdbcTemplate.update("UPDATE stellar_sku SET warn_stock = ?, update_time = NOW() WHERE id = ?", warnStock, skuId);
        }
        return Result.success();
    }
}