package com.stellar.task.expire;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动取消（延迟消息 + 消费组）配置，对应 application.yml 的 stellar.order.expire.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stellar.order.expire")
public class OrderExpireProperties {

    /** 是否启用「延迟队列 + Streams 消费组」事件驱动链路；关闭则退化为纯轮询对账。 */
    private boolean enabled = true;

    /** 订单超时时间（分钟）。⚠️ 必须与 OrderServiceImpl.ORDER_EXPIRE_MINUTES 保持一致。 */
    private int delayMinutes = 15;

    /** 延迟队列 key（ZSET：member = orderId，score = 到期毫秒）。 */
    private String delayKey = "stellar:order:expire:delay";

    /** 到期订单投递的目标 Stream key。 */
    private String streamKey = "stellar:order:expire:stream";

    /** 消费组名。 */
    private String group = "order-expire-group";

    /** Stream 近似保留上限，防止无界增长。 */
    private long streamMaxLen = 10000;

    /** 到期扫描间隔（毫秒）。 */
    private long pollIntervalMs = 1000;

    /** 单次最多取出多少条到期订单。 */
    private int pollBatchSize = 200;

    /** 消费者单次读取条数。 */
    private int consumeBatchSize = 50;

    /** 对账扫描间隔（毫秒）：兜底捞回丢失的延迟消息，默认 10 分钟。 */
    private long reconcileIntervalMs = 600_000;
}
