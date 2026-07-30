package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评价回复（用户之间互相回复评价）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewComment implements Serializable {

    private Long id;
    /** 所属评价 ID */
    private Long reviewId;
    /** 回复用户 ID */
    private Long userId;
    /** 回复内容 */
    private String content;
    /** 1=正常 0=隐藏 */
    private Integer status;
    /** 回复时间 */
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;

    // ---- 以下非 DB 字段（JOIN 填充） ----
    /** 回复用户昵称 */
    private String username;
    /** 回复用户头像 */
    private String avatar;
}
