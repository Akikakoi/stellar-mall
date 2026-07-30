package com.stellar.elasticsearch.repo;

import com.stellar.elasticsearch.doc.SpuDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * SPU ES 数据访问层 — 继承 Spring Data ES 标准 Repository，
 * 自动提供 save / findById / deleteById / count 等基础操作。
 */
@Repository
public interface SpuEsRepository extends ElasticsearchRepository<SpuDocument, Long> {
}
