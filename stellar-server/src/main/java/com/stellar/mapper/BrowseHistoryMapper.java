package com.stellar.mapper;

import com.stellar.entity.BrowseHistory;
import com.stellar.vo.BrowseHistoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * C 端商品浏览历史 Mapper。表：stellar_browse_history。
 */
@Mapper
public interface BrowseHistoryMapper {

    /** 记录浏览：存在则刷新 browse_time（upsert）。 */
    int upsert(BrowseHistory history);

    /** 淘汰该用户超过 maxKeep 的最旧一条记录（超出上限时生效）。 */
    int deleteOverLimit(@Param("userId") Long userId, @Param("maxKeep") int maxKeep);

    /** 分页查询浏览历史（含 SPU 快照），按最近浏览时间倒序。 */
    List<BrowseHistoryVO> page(@Param("userId") Long userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 当前用户浏览历史总数。 */
    long countByUserId(@Param("userId") Long userId);

    /** 删除单条记录（按 userId + id 双条件，防止横向越权）。 */
    int deleteById(@Param("userId") Long userId, @Param("id") Long id);

    /** 清空当前用户全部浏览记录。 */
    int clearByUserId(@Param("userId") Long userId);
}