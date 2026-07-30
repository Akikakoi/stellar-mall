package com.stellar.mapper;

import com.stellar.entity.PointsRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分规则 Mapper。
 */
@Mapper
public interface PointsRuleMapper {

    int insert(PointsRule rule);

    int update(PointsRule rule);

    int deleteById(@Param("id") Long id);

    PointsRule getById(@Param("id") Long id);

    PointsRule getByType(@Param("ruleType") String ruleType);

    List<PointsRule> listAll();

    List<PointsRule> listEnabled();
}
