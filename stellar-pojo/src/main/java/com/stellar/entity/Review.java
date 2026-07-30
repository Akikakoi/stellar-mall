package com.stellar.entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review implements Serializable {
    private Long id;
    private Long userId;
    private Long spuId;
    private Long skuId;
    private Long orderId;
    private String orderNo;
    private Integer rating;       // 1-5 star rating
    private String content;       // review text
    private String pics;          // comma-separated image URLs
    private String reply;         // admin reply
    private LocalDateTime replyTime;
    private Integer status;       // 1=visible 0=hidden
    private String username;      // non-DB: user nickname
    private String avatar;        // non-DB: user avatar
    private String spuName;       // non-DB: SPU product name
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;
}