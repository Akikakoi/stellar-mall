package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到记录实体，映射 stellar_checkin_record 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    /** 签到日期 */
    private LocalDate checkinDate;
    /** 获得积分 */
    private Integer pointsEarned;
    private LocalDateTime createTime;
}
