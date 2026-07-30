package com.stellar.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 首页模块批量排序 DTO
 */
@Data
public class HomeModuleBatchSortDTO {

    @NotNull(message = "排序列表不能为空")
    private List<SortItem> items;

    @Data
    public static class SortItem {
        @NotNull
        private Long id;
        @NotNull
        private Integer sortOrder;
    }
}
