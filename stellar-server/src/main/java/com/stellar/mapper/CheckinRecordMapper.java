package com.stellar.mapper;

import com.stellar.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 签到记录 Mapper。
 */
@Mapper
public interface CheckinRecordMapper {

    int insert(CheckinRecord record);

    /** 查询用户某日签到记录 */
    CheckinRecord getByUserAndDate(@Param("userId") Long userId,
                                   @Param("date") LocalDate date);

    /** 查询用户本月签到日期列表 */
    java.util.List<LocalDate> listDatesByMonth(@Param("userId") Long userId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}
