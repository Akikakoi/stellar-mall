package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.annotation.RateLimit;
import com.stellar.annotation.RequireRole;
import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.dto.EmployeeCreateDTO;
import com.stellar.dto.EmployeeLoginDTO;
import com.stellar.dto.EmployeeUpdateDTO;
import com.stellar.entity.Employee;
import com.stellar.exception.BaseException;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.EmployeeService;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.TokenBlacklistService;
import com.stellar.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端员工 Controller（登录/登出/分页/启停/新增/修改/查询）。
 * <p>
 * 路径前缀 /admin/employee，已被 JwtTokenAdminInterceptor 拦截（除了 /login）。
 */
@RestController
@RequestMapping("/admin/employee")
@Api(tags = "管理端员工相关接口")
@Slf4j
public class EmployeeController {

    private final EmployeeService employeeService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;

    @Autowired
    public EmployeeController(EmployeeService employeeService, LoginAttemptService loginAttemptService,
                              TokenBlacklistService tokenBlacklistService) {
        this.employeeService = employeeService;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @RateLimit(key = "admin-login", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/login")
    @ApiOperation("员工登录（返回 JWT，前端用 token=xxx / Authorization=Bearer xxx 两种 header 都可以）")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO dto) {
        log.info("[EmployeeController] login request, username={}", dto.getUsername());
        if (dto.getUsername() == null || dto.getPassword() == null) {
            throw new BaseException(MessageConstant.LOGIN_FAILED);
        }
        EmployeeLoginVO vo = employeeService.login(dto);
        return Result.success(vo);
    }

    @PostMapping("/logout")
    @ApiOperation("员工登出（E4：access+refresh 写黑名单，前端清 localStorage）")
    public Result<Void> logout(@RequestHeader(value = "token", required = false) String tokenHeader,
                               @RequestHeader(value = "Authorization", required = false) String authHeader,
                               @RequestBody(required = false) LogoutRequest req) {
        // E4: access token 写黑名单（从 token 或 Authorization header 提取）
        String accessToken = extractToken(tokenHeader, authHeader);
        if (accessToken != null) {
            tokenBlacklistService.blacklist(accessToken);
        }
        // E4: refresh token 写黑名单（从 body 传入）
        if (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isEmpty()) {
            tokenBlacklistService.blacklist(req.getRefreshToken());
        }
        employeeService.logout();
        return Result.success();
    }

    /**
     * 从多种 header 形式中提取 JWT。
     */
    private String extractToken(String tokenHeader, String authHeader) {
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            return tokenHeader;
        }
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7);
        }
        return null;
    }

    @PostMapping("/refresh")
    @ApiOperation("用 refresh token 换新的 access + refresh token")
    public Result<EmployeeLoginVO> refresh(@RequestBody RefreshRequest req) {
        EmployeeLoginVO vo = employeeService.refresh(req.getRefreshToken());
        return Result.success(vo);
    }

    @RequireRole({1})
    @PostMapping("/unlock")
    @ApiOperation("手动解锁被临时锁定的账号（E2 登录失败次数过多）")
    public Result<Void> unlock(@RequestBody UnlockRequest req) {
        loginAttemptService.unlock(req.getType(), req.getAccount());
        return Result.success();
    }

    @lombok.Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @lombok.Data
    public static class UnlockRequest {
        /** 账号类型：employee / mall_user */
        private String type;
        /** 账号标识：username / email */
        private String account;
    }

    @lombok.Data
    public static class LogoutRequest {
        /** refresh token（access token 从 header 提取，不放在 body） */
        private String refreshToken;
    }

    @RequireRole({1})
    @GetMapping("/page")
    @ApiOperation("员工分页查询")
    public Result<PageResult> page(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[EmployeeController] page, name={}, status={}, role={}, page={}, pageSize={}", name, status, role, page, pageSize);
        return Result.success(employeeService.page(name, status, role, page, pageSize));
    }

    @RequireRole({1})
    @GetMapping("/{id}")
    @ApiOperation("根据 ID 查询员工")
    public Result<Employee> getById(@PathVariable Long id) {
        return Result.success(employeeService.getById(id));
    }

    @RequireRole({1})
    @Idempotent(keyPrefix = "admin-employee-create", windowSeconds = 300)
    @PostMapping
    @ApiOperation("新增员工")
    public Result<Void> create(@RequestBody EmployeeCreateDTO dto) {
        log.info("[EmployeeController] create, username={}, operator EMP_ID={}",
                dto.getUsername(), BaseContext.getCurrentId());
        employeeService.create(dto);
        return Result.success();
    }

    @RequireRole({1})
    @PutMapping
    @ApiOperation("修改员工（含密码修改：password 传明文即可，Service 内部会 BCrypt 哈希）")
    public Result<Void> update(@RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(dto);
        return Result.success();
    }

    @RequireRole({1})
    @PostMapping("/status/{status}")
    @ApiOperation("启用/禁用员工：status=1 启用，status=0 禁用")
    public Result<Void> setStatus(@PathVariable Integer status,
                                  @RequestParam Long id) {
        log.info("[EmployeeController] setStatus id={}, status={}, operator={}",
                id, status, BaseContext.getCurrentId());
        // 种子超管（id=1）不允许禁用；修复原死代码 JwtClaimsConstant.EMP_ID.equals("EMP_ID") 恒真判断
        Employee emp = employeeService.getById(id);
        if (emp != null && emp.getId() != null && emp.getId() == 1
                && status != null && status == 0) {
            throw new BaseException("超级管理员账号不允许禁用");
        }
        employeeService.setStatus(id, status);
        return Result.success();
    }

    @GetMapping("/me")
    @ApiOperation("获取当前登录员工信息")
    public Result<Employee> me() {
        Long empId = BaseContext.getCurrentId();
        if (empId == null) return Result.success(null);
        return Result.success(employeeService.getById(empId));
    }
}
