package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 签到结果 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinVO {

    /** 是否签到成功 */
    private Boolean success;
    /** 获得积分 */
    private Integer pointsEarned;
    /** 提示信息 */
    private String message;
}
