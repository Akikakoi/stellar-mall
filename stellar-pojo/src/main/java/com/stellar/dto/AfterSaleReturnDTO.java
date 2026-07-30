package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户提交退货物流 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleReturnDTO {

    /** 售后单 ID */
    private Long id;
    /** 退货快递单号 */
    private String returnTracking;
}
