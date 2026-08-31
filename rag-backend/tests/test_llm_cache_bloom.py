"""布隆过滤器误判率测试。

验证 llm_cache._BloomFilter 两个核心性质：
1. 零假阴性：所有已插入的 key 必然被 `contains` 命中。
2. 误判率（假阳性率）受控：实测 FPR 严格低于理论公式 (1-e^{-kn/m})^k 给出的上界
   （给采样噪声留 1.5 倍余量），从而校验哈希分布的均匀性。

运行：
    cd rag-backend
    python -m pytest tests/test_llm_cache_bloom.py -v
"""
import math

from app.rag.llm_cache import _BloomFilter


def _theoretical_fpr(num_bits: int, num_hashes: int, n_inserted: int) -> float:
    """布隆过滤器理论误判率：(1 - e^{-kn/m})^k。"""
    return (1 - math.exp(-num_hashes * n_inserted / num_bits)) ** num_hashes


def _distinct_keys(prefix: str, count: int):
    """构造 count 个互不相同的查询式 key。"""
    return [f"{prefix}-商品规格-{i}-200W 快充" for i in range(count)]


def test_zero_false_negative():
    """已插入的 key 必须 100% 命中（布隆不允许假阴性）。"""
    bloom = _BloomFilter(num_bits=65536, num_hashes=7)
    inserted = _distinct_keys("query-a", 5000)
    for k in inserted:
        bloom.add(k)
    for k in inserted:
        assert bloom.contains(k), f"插入过的 key 不应误判为不存在: {k}"


def test_false_positive_rate_below_theoretical():
    """实测误判率 < 理论误判率；参数取默认配置（65536 bit, 7 哈希）。"""
    num_bits, num_hashes = 65536, 7
    n_inserted = 5000
    bloom = _BloomFilter(num_bits=num_bits, num_hashes=num_hashes)
    for k in _distinct_keys("query-b", n_inserted):
        bloom.add(k)

    # 用大量未插入的 key 探测误判
    probes = _distinct_keys("query-absent", 50000)
    fp = sum(1 for k in probes if bloom.contains(k))

    theoretical = _theoretical_fpr(num_bits, num_hashes, n_inserted)
    measured = fp / len(probes)
    # 理论值约 2.1e-3，样本量 5w 期望约 104 次误判；1.5 倍余量覆盖约 5 个 σ，避免偶发抖动
    upper = theoretical * 1.5 + 5e-4
    assert measured > 0, "误判率不应为 0（样本规模下理论上必然出现误判）"
    assert measured < upper, (
        f"误判率超限: measured={measured:.6f} > upper={upper:.6f} "
        f"(theoretical={theoretical:.6f}, fp={fp}/{len(probes)})"
    )


def test_false_positive_rate_not_saturated():
    """布隆不应被打满（满位数组会让误判率趋近 1）。"""
    bloom = _BloomFilter(num_bits=65536, num_hashes=7)
    for k in _distinct_keys("query-c", 5000):
        bloom.add(k)
    probes = _distinct_keys("query-absent-2", 50000)
    fp = sum(1 for k in probes if bloom.contains(k))
    measured = fp / len(probes)
    # 健康实现（n=5000,k=7,m=65536）实测约 2e-3，远低于 5e-2；
    # 若实现"全置位"或哈希失效，此处会飙到 ~1
    assert measured < 5e-2, f"误判率过高，疑似布隆失效: measured={measured:.4f}"


def test_fpr_scales_with_load():
    """插入越多，误判率单调不降（随容量占用上升）。"""
    num_bits, num_hashes = 65536, 7
    probes = _distinct_keys("query-absent-3", 50000)

    def _measure(n):
        bloom = _BloomFilter(num_bits, num_hashes)
        for k in _distinct_keys("query-d", n):
            bloom.add(k)
        return sum(1 for k in probes if bloom.contains(k)) / len(probes)

    low = _measure(2000)
    high = _measure(20000)
    assert high >= low - 1e-4, f"容量升高后误判率不应下降: low={low:.5f}, high={high:.5f}"