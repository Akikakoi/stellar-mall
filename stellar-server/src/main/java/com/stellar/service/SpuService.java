package com.stellar.service;

import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Spu;
import com.stellar.result.PageResult;

import java.math.BigDecimal;
import java.util.List;

public interface SpuService {

    /** 新增 SPU + 嵌套 SKU，返回 spuId。 */
    Long saveWithSkus(SpuSaveDTO dto);

    Spu getById(Long id);

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
                                 Long categoryId, Long category2Id,
                                 Integer status) {
        return pageQuery(page, pageSize, name, categoryId, category2Id,
                status, null, null, null, null);
    }

    /** 分页查询。注意：DTO 里的字段为 null 时表示不过滤。 */
    PageResult pageQuery(Integer page, Integer pageSize, String name,
                         Long categoryId, Long category2Id,
                         Integer status, Integer isNew, Integer isHot,
                         BigDecimal priceFrom, BigDecimal priceTo);

    /** 按 DTO 分页（供 Controller 直接转发）。 */
    default PageResult pageQueryByDto(SpuPageQueryDTO dto) {
        if (dto == null) dto = new SpuPageQueryDTO();
        return pageQuery(dto.getPage(), dto.getPageSize(), dto.getName(),
                dto.getCategoryId(), dto.getCategory2Id(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo());
    }
}
