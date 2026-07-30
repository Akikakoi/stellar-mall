package com.stellar.service;

import com.stellar.dto.CartAddDTO;
import com.stellar.dto.CartUpdateDTO;
import com.stellar.vo.CartVO;

import java.util.List;

public interface CartService {

    /** 添加购物车：同 SKU 已存在则合并 qty，不存在则新增。默认 checked=1。 */
    void add(Long userId, CartAddDTO dto);

    /** 列表：当前用户的全部购物车项，按 id 倒序。 */
    List<CartVO> list(Long userId);

    /** 更新 qty 或 checked（两字段独立可选，传哪个改哪个）。 */
    void update(Long userId, CartUpdateDTO dto);

    /** 删除指定购物车记录。归属校验：只允许删自己的。 */
    void delete(Long userId, Long cartId);

    /** 清空当前用户的所有购物车记录。 */
    void clear(Long userId);
}
