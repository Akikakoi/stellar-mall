package com.stellar.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页查询封装（完全对齐 sky PageResult）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult implements Serializable {
    /** 总记录数 */
    private Long total;
    /** 当前页记录列表 */
    private List records;
}
