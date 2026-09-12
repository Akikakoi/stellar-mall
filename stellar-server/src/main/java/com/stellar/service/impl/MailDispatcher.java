package com.stellar.service.impl;

import com.stellar.config.AsyncExecutorConfig;
import com.stellar.entity.NotificationLog;
import com.stellar.mapper.NotificationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 邮件投递组件：把「生成验证码并落库」与「真实发信」这两件事拆开。
 *
 * <p>之所以单独拆一个 Bean，有两个硬性原因：</p>
 * <ol>
 *   <li><b>事务边界</b>：{@code javaMailSender.send()} 是阻塞网络 IO（SMTP 数百毫秒起），
 *       留在调用方的 {@code @Transactional} 里会让数据库事务和连接被一起拖住；</li>
 *   <li><b>代理边界</b>：{@code @Async} 依赖 Spring 代理生效，同一个 Bean 内部自调用
 *       不经过代理、异步会静默失效，所以发信必须由另一个 Bean 承载。</li>
 * </ol>
 *
 * <p>调用方见 {@link NotificationServiceImpl#sendEmailCode}：落库后把发信注册到
 * {@code afterCommit}，事务提交成功才真正投递。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailDispatcher {

    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;
    private final NotificationLogMapper notificationLogMapper;

    /** 是否启用真实 SMTP 发送；false 时为开发模式，不真实发信 */
    @Value("${stellar.mail.enabled:false}")
    private boolean mailEnabled;

    /**
     * 异步投递邮箱验证码。
     *
     * <p>前置条件由调用方保证：验证码已落库且事务已提交。本方法内部异常只记日志、
     * 绝不回抛——验证码已经入库，前端仍可正常校验，投递失败不该影响注册/改密主流程。</p>
     *
     * @param email 收件邮箱
     * @param type  验证码类型（仅用于日志与通知流水）
     * @param code  已落库的验证码明文
     */
    @Async(AsyncExecutorConfig.NOTIFICATION_EXECUTOR)
    public void sendVerifyCodeMail(String email, String type, String code) {
        String content = "【星耀商城】您的验证码是 " + code + "，5分钟内有效。若非本人操作请忽略。";

        if (!mailEnabled) {
            // 开发模式：未配置 SMTP，不真实发信，验证码打日志由前端兜底展示
            log.info("[邮箱验证码] 开发模式（未配置 SMTP，不真实发送） 邮箱:{} 类型:{} 验证码:{}", email, type, code);
            recordLog(email, content, 0, "SMTP 未配置");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getUsername());
            message.setTo(email);
            message.setSubject("【星耀商城】验证码");
            message.setText(content);
            javaMailSender.send(message);
            log.info("[邮箱验证码] 已发送 邮箱:{} 类型:{} 验证码:{}", email, type, code);
            recordLog(email, content, 1, null);
        } catch (Exception e) {
            log.error("[邮箱验证码] 发送失败 邮箱:{} 类型:{}", email, type, e);
            recordLog(email, content, 2, e.getMessage());
        }
    }

    /**
     * 通知日志落库。
     * <p>此处同步写库而非再套一层 {@code @Async}：当前已处于发信线程，再异步一次只会让
     * 排错时更难对时序；写失败只告警，不影响发信结果。</p>
     */
    private void recordLog(String email, String content, int status, String errorMsg) {
        try {
            notificationLogMapper.insert(NotificationLog.builder()
                    .userId(null)
                    .phone(null)
                    .email(email)
                    .channel("EMAIL")
                    .type("VERIFY_CODE")
                    .title("验证码")
                    .content(content)
                    .status(status)
                    .sendTime(status == 1 ? LocalDateTime.now() : null)
                    .errorMsg(errorMsg)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("通知日志写入失败（已忽略）: {}", e.getMessage());
        }
    }
}
