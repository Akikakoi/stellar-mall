package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后审核 DTO（管理端审批）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleAuditDTO {

    /** 售后单 ID */
    private Long id;
    /** 审核结果：true 通过，false 拒绝 */
    private Boolean approved;
    /** 审核备注 */
    private String remark;
}
