package com.stellar.controller.user;

import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.elasticsearch.service.SearchSuggestService;
import com.stellar.elasticsearch.service.SpuSearchService;
import com.stellar.entity.Spu;
import com.stellar.result.Result;
import com.stellar.service.SpuService;
import com.stellar.vo.SearchResultVO;
import com.stellar.vo.SearchSuggestVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C 端商品 Controller：/user/spu。
 *   - GET /user/spu/page     → 分页搜索（含高亮 + 聚合）
 *   - GET /user/spu/suggest  → 搜索建议（自动补全 + 拼写纠错）
 *   - GET /user/spu/{id}     → SPU 详情（含 skuList）
 */
@RestController
@RequestMapping("/user/spu")
@RequiredArgsConstructor
@Api(tags = "C端：商品")
public class UserSpuController {

    private final SpuService spuService;
    private final SpuSearchService spuSearchService;
    private final SearchSuggestService suggestService;

    @GetMapping("/page")
    @ApiOperation("C 端 SPU 分页搜索（含高亮 + 聚合，默认过滤上架）")
    public Result<SearchResultVO> page(SpuPageQueryDTO dto) {
        if (dto == null) dto = new SpuPageQueryDTO();
        if (dto.getStatus() == null) dto.setStatus(1);
        return Result.success(spuSearchService.searchWithHighlight(dto));
    }

    @GetMapping("/suggest")
    @ApiOperation("搜索建议：自动补全 + 拼写纠错")
    public Result<SearchSuggestVO> suggest(@RequestParam String prefix) {
        return Result.success(suggestService.suggest(prefix));
    }

    @GetMapping("/{id}")
    @ApiOperation("SPU 详情（含嵌套 SKU 列表）")
    public Result<Spu> detail(@PathVariable Long id) {
        Spu spu = spuService.getById(id);
        if (spu != null && spu.getStatus() != null && spu.getStatus() != 1) {
            return Result.error("该商品已下架");
        }
        return Result.success(spu);
    }
}
