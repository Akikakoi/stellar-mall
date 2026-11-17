package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.constant.JwtClaimsConstant;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.elasticsearch.service.SearchSuggestService;
import com.stellar.elasticsearch.service.SpuSearchService;
import com.stellar.entity.Spu;
import com.stellar.properties.JwtProperties;
import com.stellar.result.Result;
import com.stellar.service.RecommendService;
import com.stellar.service.SpuService;
import com.stellar.utils.JwtUtil;
import com.stellar.vo.OrbitSpuVO;
import com.stellar.vo.RecommendResultVO;
import com.stellar.vo.SearchResultVO;
import com.stellar.vo.SearchSuggestVO;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;

import java.math.BigDecimal;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * C 端商品 Controller：/user/spu。
 *   - GET /user/spu/page     → 分页搜索（含高亮 + 聚合）
 *   - GET /user/spu/suggest  → 搜索建议（自动补全 + 拼写纠错）
 *   - GET /user/spu/random   → 随机抽取上架商品（保留给旧调用方）
 *   - GET /user/spu/orbit    → 首页首屏环绕商品（候选池优先 + 随机兜底，轻量字段）
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
    private final RecommendService recommendService;
    private final JwtProperties jwtProperties;

    @RateLimit(key = "spu-search", maxRequests = 60, windowSeconds = 60)
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

    @RateLimit(key = "spu-random", maxRequests = 60, windowSeconds = 60)
    @GetMapping("/random")
    @ApiOperation("随机抽取上架且有主图的商品（保留给旧调用方，最多 20 个）")
    public Result<List<Spu>> random(@RequestParam(defaultValue = "6") int count) {
        return Result.success(spuService.listRandom(count));
    }

    @RateLimit(key = "spu-orbit", maxRequests = 120, windowSeconds = 60)
    @GetMapping("/orbit")
    @ApiOperation("首页首屏环绕商品：候选池优先、随机兜底，一次返回轻量卡片数据（默认 8 个，最多 20 个）")
    public Result<List<OrbitSpuVO>> orbit(
            @RequestParam(defaultValue = "8") int count,
            @RequestParam(name = "exclude", required = false) String exclude) {
        return Result.success(spuService.listOrbit(count, parseExcludeIds(exclude)));
    }

    /**
     * 解析前端传来的「最近展示商品 id」：逗号分隔，非法项直接忽略。
     * 不用 {@code List<Long>} 直接绑定是因为参数里出现非数字会导致 500，
     * 而这一参数只是优化项，任何脏值都应该被宽松忽略。
     */
    private List<Long> parseExcludeIds(String exclude) {
        if (exclude == null || exclude.isBlank()) return List.of();
        return Arrays.stream(exclude.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                })
                .filter(id -> id != null && id > 0)
                .toList();
    }

    @RateLimit(key = "spu-recommend", maxRequests = 120, windowSeconds = 60)
    @GetMapping("/recommend")
    @ApiOperation("C 端混合推荐：ItemCF + 类目/价格偏好 + 热度兜底（登录可选）")
    public Result<RecommendResultVO> recommend(
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(required = false) Long seedSpuId,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String deviceId,
            HttpServletRequest request) {
        return Result.success(recommendService.recommend(
                resolveOptionalUserId(request), deviceId, limit, seedSpuId, maxPrice));
    }

    /** 推荐接口允许游客访问：能解析到有效 C 端 token 就个性化，否则走热度冷启动。 */
    private Long resolveOptionalUserId(HttpServletRequest request) {
        String token = request.getHeader(jwtProperties.getUserTokenName());
        if (token == null || token.isEmpty()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = auth.substring(7);
            }
        }
        if (token == null || token.isEmpty()) return null;
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Object uid = claims.get(JwtClaimsConstant.USER_ID);
            if (uid instanceof Number number) return number.longValue();
            return uid == null ? null : Long.valueOf(uid.toString());
        } catch (Exception e) {
            return null;
        }
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
