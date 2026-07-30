package com.stellar.controller.admin;

import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.SpuService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/recycle")
@RequiredArgsConstructor
@Api(tags = "管理端：商品回收站")
public class RecycleBinController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    @ApiOperation("回收站分页（status=0的已删除SPU）")
    public Result<PageResult> page(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                   @RequestParam(required = false) String name) {
        int offset = (page - 1) * pageSize;
        StringBuilder where = new StringBuilder("WHERE s.status = 0");
        List<Object> params = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            where.append(" AND s.name LIKE ?");
            params.add("%" + name + "%");
        }
        params.add(pageSize);
        params.add(offset);
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
            "SELECT s.*, c1.name AS category_name FROM stellar_spu s LEFT JOIN stellar_category c1 ON c1.id = s.category_id " + where + " ORDER BY s.update_time DESC LIMIT ? OFFSET ?", params.toArray());
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stellar_spu s " + where, Long.class, params.subList(0, params.size() - 2).toArray());
        return Result.success(new PageResult(total == null ? 0L : total, list == null ? new ArrayList<>() : list));
    }

    @PostMapping("/{id}/restore")
    @ApiOperation("恢复商品（status 0→1）")
    public Result<String> restore(@PathVariable Long id) {
        jdbcTemplate.update("UPDATE stellar_spu SET status = 1, on_shelf_time = NOW(), update_time = NOW() WHERE id = ?", id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("彻底删除")
    public Result<String> forceDelete(@PathVariable Long id) {
        jdbcTemplate.update("DELETE FROM stellar_sku WHERE spu_id = ?", id);
        jdbcTemplate.update("DELETE FROM stellar_spu WHERE id = ?", id);
        return Result.success();
    }

    @PostMapping("/batch-restore")
    @ApiOperation("批量恢复")
    public Result<String> batchRestore(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            for (Integer id : ids) {
                jdbcTemplate.update("UPDATE stellar_spu SET status = 1, on_shelf_time = NOW(), update_time = NOW() WHERE id = ?", id.longValue());
            }
        }
        return Result.success();
    }

    @PostMapping("/batch-delete")
    @ApiOperation("批量彻底删除")
    public Result<String> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            for (Integer id : ids) {
                jdbcTemplate.update("DELETE FROM stellar_sku WHERE spu_id = ?", id.longValue());
                jdbcTemplate.update("DELETE FROM stellar_spu WHERE id = ?", id.longValue());
            }
        }
        return Result.success();
    }

    @DeleteMapping("/clear")
    @ApiOperation("清空回收站")
    public Result<String> clear() {
        jdbcTemplate.update("DELETE FROM stellar_sku WHERE spu_id IN (SELECT id FROM stellar_spu WHERE status = 0)");
        jdbcTemplate.update("DELETE FROM stellar_spu WHERE status = 0");
        return Result.success();
    }
}