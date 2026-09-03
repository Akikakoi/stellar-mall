package com.stellar.controller.user;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.dto.BehaviorTrackDTO;
import com.stellar.entity.UserBehavior;
import com.stellar.properties.JwtProperties;
import com.stellar.result.Result;
import com.stellar.service.UserBehaviorService;
import com.stellar.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * C 端行为埋点采集：POST /user/behavior/track（批量上报）。
 * <p>鉴权策略：接口放行（游客也埋点），登录用户的 userId 由后端「可选解析」token 补齐——
 * 能解析到有效 C 端 token 就记录用户 ID，否则按游客处理（deviceId 归因）。
 * 全程 try-catch 静默：埋点失败对前端永远返回成功，绝不拖累主流程。</p>
 */
@Slf4j
@RestController
@RequestMapping("/user/behavior")
@RequiredArgsConstructor
@Api(tags = "C端：行为埋点")
public class BehaviorController {

    private static final int MAX_EVENTS_PER_REQUEST = 100;
    private static final int MAX_EXTRA_LEN = 500;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserBehaviorService behaviorService;
    private final JwtProperties jwtProperties;

    @PostMapping("/track")
    @ApiOperation("行为埋点批量上报（游客/登录用户均可，事件类型见 UserBehavior）")
    public Result<String> track(@RequestBody(required = false) BehaviorTrackDTO dto,
                                HttpServletRequest req) {
        try {
            if (dto == null || dto.getEvents() == null || dto.getEvents().isEmpty()) {
                return Result.success();
            }
            List<UserBehavior> rows = buildRows(dto, req);
            behaviorService.trackAsync(rows);
        } catch (Exception e) {
            // 任何异常都不回传失败（埋点是尽力而为）
            log.warn("[behavior] track ignored: {}", e.getMessage());
        }
        return Result.success();
    }

    /** 组装入库实体：字段截断兜底，防止脏数据导致整批 insert 失败。 */
    private List<UserBehavior> buildRows(BehaviorTrackDTO dto, HttpServletRequest req) {
        Long userId = resolveUserId(req);
        String deviceId = truncate(dto.getDeviceId(), 64);
        String clientIp = req.getRemoteAddr();
        String ua = truncate(req.getHeader("User-Agent"), 255);

        int n = Math.min(dto.getEvents().size(), MAX_EVENTS_PER_REQUEST);
        List<UserBehavior> rows = new ArrayList<>(n);
        for (BehaviorTrackDTO.BehaviorEventDTO e : dto.getEvents()) {
            if (e == null) continue;
            String eventType = truncate(e.getEventType(), 32);
            if (eventType == null || eventType.isEmpty()) continue;

            BigDecimal amount = sanitizeAmount(e.getAmount());
            rows.add(UserBehavior.builder()
                    .userId(userId)
                    .deviceId(deviceId)
                    .eventType(eventType)
                    .spuId(e.getSpuId())
                    .skuId(e.getSkuId())
                    .categoryId(e.getCategoryId())
                    .keyword(truncate(e.getKeyword(), 100))
                    .scene(truncate(e.getScene(), 32))
                    .position(e.getPosition())
                    .amount(amount)
                    .durationMs(e.getDurationMs())
                    .extra(extraToJson(e.getExtra()))
                    .clientIp(clientIp)
                    .userAgent(ua)
                    .build());
        }
        return rows;
    }

    /** 可选鉴权：能解析到有效 C 端 token 返回 userId，否则（游客/过期）返回 null。 */
    private Long resolveUserId(HttpServletRequest req) {
        String token = req.getHeader(jwtProperties.getUserTokenName()); // authentication
        if (token == null || token.isEmpty()) {
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = auth.substring(7);
            }
        }
        if (token == null || token.isEmpty()) return null;
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Object uid = claims.get(JwtClaimsConstant.USER_ID);
            return uid == null ? null : ((Number) uid).longValue();
        } catch (Exception e) {
            return null; // 无效/过期 token 按游客处理，不阻断埋点
        }
    }

    private static BigDecimal sanitizeAmount(BigDecimal amount) {
        if (amount == null) return null;
        try {
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /** extra 允许任意 JSON（对象/数组/字符串），统一序列化为字符串入库。 */
    private static String extraToJson(Object extra) {
        if (extra == null) return null;
        if (extra instanceof String s) return truncate(s, MAX_EXTRA_LEN);
        try {
            return truncate(MAPPER.writeValueAsString(extra), MAX_EXTRA_LEN);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
