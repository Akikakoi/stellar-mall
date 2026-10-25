package com.stellar.service;

import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Spu;
import com.stellar.result.PageResult;
import com.stellar.vo.OrbitSpuVO;

import java.math.BigDecimal;
import java.util.List;

public interface SpuService {

    /** 新增 SPU + 嵌套 SKU，返回 spuId。 */
    Long saveWithSkus(SpuSaveDTO dto);

    Spu getById(Long id);

    /**
     * 随机抽取上架且有主图的 SPU（首页首屏环绕商品用）。
     *
     * @param count 抽取数量，内部限制在 1~20
     * @return 随机商品列表
     */
    List<Spu> listRandom(int count);

    /**
     * 首页首屏环绕商品：管理端配置的候选池优先，未配置或候选全部失效时从商品库随机抽取。
     * <p>
     * 合并「配置候选池 + 随机兜底」两步，一次返回卡片需要的轻量字段，
     * 让首屏只需一个请求（原先配置接口 + 随机接口 / N 个详情接口）。
     * </p>
     *
     * @param count 展示数量，内部限制在 1~20
     * @return 轻量卡片数据（id/name/mainImage）
     */
    default List<OrbitSpuVO> listOrbit(int count) {
        return listOrbit(count, List.of());
    }

    /**
     * 同上，但随机抽取时优先排除 {@code excludeIds}（最近展示过的商品）。
     * 排除后候选不足时会回退补足，保证尽量返回 count 个不重复商品。
     *
     * @param count     展示数量，内部限制在 1~20
     * @param excludeIds 最近展示过的商品 id（会去重、过滤非法值、限制最大数量）
     * @return 轻量卡片数据
     */
    List<OrbitSpuVO> listOrbit(int count, List<Long> excludeIds);

    /** 更新 SPU（含 SKU 覆盖式同步）。 */
    void updateWithSkus(SpuSaveDTO dto);

    /** 上下架：status=1 上架 / 0 下架，同步 onShelfTime/offShelfTime。 */
    void onOffShelf(Long id, Integer status);

    /** 批量上下架：status=1 上架 / 0 下架。 */
    void batchOnOffShelf(List<Long> ids, Integer status);

    /** 按 id 删除 SPU（同步删所有 SKU）。 */
    void deleteById(Long id);

    /** 分页查询（简化版，供测试/历史代码调用：仅 name+分类+状态）。 */
    default PageResult pageQuery(Integer page, Integer pageSize, String name,
                                 Long categoryId,
                                 Integer status) {
        return pageQuery(page, pageSize, name, categoryId,
                status, null, null, null, null);
    }

    /** 分页查询。注意：DTO 里的字段为 null 时表示不过滤。 */
    PageResult pageQuery(Integer page, Integer pageSize, String name,
                         Long categoryId,
                         Integer status, Integer isNew, Integer isHot,
                         BigDecimal priceFrom, BigDecimal priceTo);

    /** 按 DTO 分页（供 Controller 直接转发）。 */
    default PageResult pageQueryByDto(SpuPageQueryDTO dto) {
        if (dto == null) dto = new SpuPageQueryDTO();
        return pageQuery(dto.getPage(), dto.getPageSize(), dto.getName(),
                dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo());
    }
}
