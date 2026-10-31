package com.stellar.service.impl;

import com.stellar.entity.ServiceItem;
import com.stellar.exception.BaseException;
import com.stellar.mapper.ServiceItemMapper;
import com.stellar.mapper.SpuServiceMapper;
import com.stellar.service.ServiceItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 保障服务字典服务实现。
 * <p>服务的标题/价格/启停会影响 C 端商品详情（spu:detail 缓存内含 services 字段），
 * 因此所有写操作都需要清空 spu:detail 缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceItemServiceImpl implements ServiceItemService {

    private final ServiceItemMapper serviceItemMapper;
    private final SpuServiceMapper spuServiceMapper;

    @Override
    public List<ServiceItem> listAll() {
        return serviceItemMapper.listAll();
    }

    @Override
    @Cacheable(value = "service:enabled", unless = "#result == null || #result.isEmpty()")
    public List<ServiceItem> listEnabled() {
        return serviceItemMapper.listEnabled();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"spu:detail", "service:enabled"}, allEntries = true)
    public Long save(ServiceItem item) {
        if (item == null) throw new BaseException("参数不能为空");
        if (item.getTitle() == null || item.getTitle().trim().isEmpty()) {
            throw new BaseException("服务名称不能为空");
        }
        if (item.getPrice() == null || item.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BaseException("服务价格不能为负数");
        }
        LocalDateTime now = LocalDateTime.now();
        if (item.getId() == null) {
            // 新增：code 必填且唯一（历史快照靠 code 解析，创建后不可修改）
            String code = item.getCode() == null ? null : item.getCode().trim();
            if (code == null || code.isEmpty()) {
                throw new BaseException("服务标识（code）不能为空");
            }
            if (serviceItemMapper.getByCode(code) != null) {
                throw new BaseException("服务标识（code）已存在：" + code);
            }
            if (item.getStatus() == null) item.setStatus(1);
            item.setCode(code);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            serviceItemMapper.insert(item);
            log.info("[ServiceItemService] 新增保障服务 id={} code={} title={}", item.getId(), code, item.getTitle());
        } else {
            ServiceItem old = serviceItemMapper.getById(item.getId());
            if (old == null) throw new BaseException("保障服务不存在");
            // code 创建后不可修改（历史快照兼容）
            item.setCode(old.getCode());
            item.setUpdateTime(now);
            serviceItemMapper.update(item);
            log.info("[ServiceItemService] 更新保障服务 id={} title={}", item.getId(), item.getTitle());
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"spu:detail", "service:enabled"}, allEntries = true)
    public void delete(Long id) {
        if (id == null) throw new BaseException("参数不能为空");
        ServiceItem old = serviceItemMapper.getById(id);
        if (old == null) throw new BaseException("保障服务不存在");
        int refs = spuServiceMapper.countByServiceId(id);
        if (refs > 0) {
            throw new BaseException("该服务仍被 " + refs + " 个商品关联，请先在商品编辑中取消关联后再删除");
        }
        serviceItemMapper.deleteById(id);
        log.info("[ServiceItemService] 删除保障服务 id={} code={}", id, old.getCode());
    }
}
