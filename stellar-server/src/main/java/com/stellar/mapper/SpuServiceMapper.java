package com.stellar.mapper;

import com.stellar.entity.ServiceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品-保障服务关联 Mapper。表：stellar_spu_service。
 */
@Mapper
public interface SpuServiceMapper {

    /** 查询某 SPU 已关联且启用的服务（按服务表 status 过滤） */
    List<ServiceItem> listEnabledBySpuId(@Param("spuId") Long spuId);

    /** 查询某 SPU 已关联的全部服务（含停用，管理端编辑回填用） */
    List<ServiceItem> listBySpuId(@Param("spuId") Long spuId);

    int deleteBySpuId(@Param("spuId") Long spuId);

    int insertBatch(@Param("spuId") Long spuId, @Param("serviceIds") List<Long> serviceIds);

    /** 统计某服务被多少 SPU 关联（删除前的引用守卫） */
    int countByServiceId(@Param("serviceId") Long serviceId);
}
