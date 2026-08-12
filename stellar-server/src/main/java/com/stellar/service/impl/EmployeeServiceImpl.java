package com.stellar.service.impl;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.constant.MessageConstant;
import com.stellar.constant.StatusConstant;
import com.stellar.context.BaseContext;
import com.stellar.dto.EmployeeCreateDTO;
import com.stellar.dto.EmployeeLoginDTO;
import com.stellar.dto.EmployeeUpdateDTO;
import com.stellar.entity.Employee;
import com.stellar.exception.BaseException;
import com.stellar.exception.LoginFailedException;
import com.stellar.mapper.EmployeeMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.result.PageResult;
import com.stellar.service.EmployeeService;
import com.stellar.service.LoginAttemptService;
import com.stellar.utils.JwtUtil;
import com.stellar.vo.EmployeeLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 员工服务实现类。
 * <p>
 * 提供员工登录认证、分页查询、启停、创建及更新等功能。
 * 密码使用 BCrypt 加密存储，登录时签发 JWT。
 * </p>
 */
@Slf4j
@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    /** Redis key 前缀：refresh:{type}:{id}，单设备登录时新登录覆盖旧 refresh */
    private static final String REFRESH_KEY_PREFIX = "refresh:employee:";

    private final EmployeeMapper employeeMapper;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final LoginAttemptService loginAttemptService;

    @Autowired
    public EmployeeServiceImpl(EmployeeMapper employeeMapper, JwtProperties jwtProperties,
                                StringRedisTemplate stringRedisTemplate,
                                LoginAttemptService loginAttemptService) {
        this.employeeMapper = employeeMapper;
        this.jwtProperties = jwtProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * 员工登录：
     *   1. 根据 username 查 employee
     *   2. BCrypt.matches(dto.password, employee.passwordHash)
     *   3. 检查 status 是否启用
     *   4. 用 admin-secret-key 签发 JWT（claims = {EMP_ID, ROLE, NAME}）
     */
    @Override
    public EmployeeLoginVO login(EmployeeLoginDTO dto) {
        // E2: 登录前检查账号是否被临时锁定（失败次数过多）
        loginAttemptService.checkLocked("employee", dto.getUsername());

        Employee emp = employeeMapper.getByUsername(dto.getUsername());
        if (emp == null) {
            loginAttemptService.recordFailure("employee", dto.getUsername());
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        if (!BCRYPT.matches(dto.getPassword(), emp.getPasswordHash())) {
            loginAttemptService.recordFailure("employee", dto.getUsername());
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        if (emp.getStatus() != null && emp.getStatus().equals(StatusConstant.DISABLE)) {
            throw new LoginFailedException(MessageConstant.ACCOUNT_LOCKED);
        }

        // E2: 登录成功，清零失败计数
        loginAttemptService.clearAttempts("employee", dto.getUsername());

        // 构造 claims（字段名必须和 JwtClaimsConstant + RAG 端约定一致，大小写敏感）
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, emp.getId());
        String roleStr = switch (emp.getRole()) {
            case 1 -> "admin";
            case 2 -> "operator";
            case 3 -> "customer-service";
            case 4 -> "finance";
            default -> "user";
        };
        claims.put(JwtClaimsConstant.ROLE, roleStr);
        claims.put(JwtClaimsConstant.NAME, emp.getName());

        String accessToken = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );
        String refreshToken = JwtUtil.createRefreshJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminRefreshTtl(),
                claims
        );
        // 单设备登录：新 refresh 覆盖旧 refresh
        stringRedisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + emp.getId(),
                refreshToken,
                jwtProperties.getAdminRefreshTtl(),
                TimeUnit.MILLISECONDS
        );
        log.info("[EmployeeService] login OK: username={}, EMP_ID={}, role={}", dto.getUsername(), emp.getId(), roleStr);

        return EmployeeLoginVO.builder()
                .id(emp.getId())
                .userName(emp.getUsername())
                .name(emp.getName())
                .role(emp.getRole())
                .token(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * 用 refresh token 换新的 access + refresh token。
     * 校验：token 可解析 + type=refresh + Redis 存的 token 与传入一致（单设备 + 一次性使用）。
     */
    @Override
    public EmployeeLoginVO refresh(String refreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), refreshToken);
        } catch (Exception e) {
            log.warn("[EmployeeService] refresh token 解析失败: {}", e.getMessage());
            throw new BaseException("refresh token 无效或已过期");
        }
        // 必须是 refresh 类型
        String type = claims.get(JwtClaimsConstant.TOKEN_TYPE, String.class);
        if (!JwtUtil.TYPE_REFRESH.equals(type)) {
            throw new BaseException("refresh token 无效或已过期");
        }
        Long empId = ((Number) claims.get(JwtClaimsConstant.EMP_ID)).longValue();

        // 校验 Redis 中的 refresh token 是否匹配（单设备 + 一次性）
        String stored = stringRedisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + empId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BaseException("refresh token 无效或已过期");
        }

        Employee emp = employeeMapper.getById(empId);
        if (emp == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (emp.getStatus() != null && emp.getStatus().equals(StatusConstant.DISABLE)) {
            throw new BaseException(MessageConstant.ACCOUNT_LOCKED);
        }

        // 重新构造 claims（不沿用旧 claims，避免 jti 重复）
        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put(JwtClaimsConstant.EMP_ID, emp.getId());
        String roleStr = switch (emp.getRole()) {
            case 1 -> "admin";
            case 2 -> "operator";
            case 3 -> "customer-service";
            case 4 -> "finance";
            default -> "user";
        };
        newClaims.put(JwtClaimsConstant.ROLE, roleStr);
        newClaims.put(JwtClaimsConstant.NAME, emp.getName());

        String newAccess = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                newClaims
        );
        String newRefresh = JwtUtil.createRefreshJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminRefreshTtl(),
                newClaims
        );
        // 覆盖 Redis：旧 refresh 失效（一次性使用）
        stringRedisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + emp.getId(),
                newRefresh,
                jwtProperties.getAdminRefreshTtl(),
                TimeUnit.MILLISECONDS
        );
        log.info("[EmployeeService] refresh OK: EMP_ID={}", empId);
        return EmployeeLoginVO.builder()
                .id(emp.getId())
                .userName(emp.getUsername())
                .name(emp.getName())
                .role(emp.getRole())
                .token(newAccess)
                .refreshToken(newRefresh)
                .build();
    }

    /**
     * 分页查询员工列表。
     * <p>
     * 返回前会清空 password_hash 字段，避免密码泄漏。
     * </p>
     *
     * @param name     员工姓名（可选，模糊匹配）
     * @param status   状态（可选）
     * @param role     角色（可选）
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult page(String name, Integer status, Integer role, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        Long total = employeeMapper.count(name, status, role);
        List<Employee> list = employeeMapper.page(name, status, role, offset, pageSize);
        // ⚠️ 返回给前端时把 password_hash 清空，避免泄漏
        list.forEach(e -> e.setPasswordHash(null));
        return new PageResult(total, list);
    }

    /**
     * 根据ID查询员工。
     * <p>
     * 返回前会清空 password_hash 字段。
     * </p>
     *
     * @param id 员工ID
     * @return 员工实体，不存在时返回null
     */
    @Override
    public Employee getById(Long id) {
        Employee e = employeeMapper.getById(id);
        if (e != null) e.setPasswordHash(null);
        return e;
    }

    /**
     * 设置员工状态（启用/禁用）。
     *
     * @param id     员工ID
     * @param status 状态值
     */
    @Override
    public void setStatus(Long id, Integer status) {
        Employee upd = Employee.builder()
                .id(id)
                .status(status)
                .build();
        // ⚠️ BaseContext.getCurrentId() 已由 JwtTokenAdminInterceptor.preHandle 写入
        employeeMapper.update(upd);
    }

    /**
     * 创建新员工。
     * <p>
     * 校验用户名唯一性，密码使用 BCrypt 加密存储。
     * </p>
     *
     * @param dto 员工创建请求
     */
    @Override
    public void create(EmployeeCreateDTO dto) {
        if (employeeMapper.getByUsername(dto.getUsername()) != null) {
            throw new BaseException("用户名已存在");
        }

        String passwordHash = BCRYPT.encode(dto.getPassword());
        Integer status = dto.getStatus() != null ? dto.getStatus() : StatusConstant.ENABLE;
        Integer role = dto.getRole() != null ? dto.getRole() : 2;

        Employee employee = Employee.builder()
                .username(dto.getUsername())
                .name(dto.getName())
                .passwordHash(passwordHash)
                .phone(dto.getPhone())
                .sex(dto.getSex() != null ? dto.getSex().toString() : null)
                .idNumber(dto.getIdNumber())
                .avatar(dto.getAvatar())
                .status(status)
                .role(role)
                .build();

        employeeMapper.insert(employee);
    }

    /**
     * 更新员工信息。
     * <p>
     * 如果传入了新密码，会使用 BCrypt 加密后更新；否则不修改密码。
     * </p>
     *
     * @param dto 员工更新请求
     */
    @Override
    public void update(EmployeeUpdateDTO dto) {
        Employee employee = Employee.builder()
                .id(dto.getId())
                .name(dto.getName())
                .phone(dto.getPhone())
                .sex(dto.getSex() != null ? dto.getSex().toString() : null)
                .idNumber(dto.getIdNumber())
                .avatar(dto.getAvatar())
                .status(dto.getStatus())
                .role(dto.getRole())
                .build();

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            employee.setPasswordHash(BCRYPT.encode(dto.getPassword()));
        }

        employeeMapper.update(employee);
    }
}
