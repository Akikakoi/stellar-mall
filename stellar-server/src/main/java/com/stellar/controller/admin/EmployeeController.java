package com.stellar.controller.admin;

import com.stellar.annotation.RateLimit;
import com.stellar.constant.JwtClaimsConstant;
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

    @Autowired
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
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
    @ApiOperation("员工登出（JWT 无状态，前端清 localStorage 即可，后端清 BaseContext）")
    public Result<Void> logout() {
        employeeService.logout();
        return Result.success();
    }

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

    @GetMapping("/{id}")
    @ApiOperation("根据 ID 查询员工")
    public Result<Employee> getById(@PathVariable Long id) {
        return Result.success(employeeService.getById(id));
    }

    @PostMapping
    @ApiOperation("新增员工")
    public Result<Void> create(@RequestBody EmployeeCreateDTO dto) {
        log.info("[EmployeeController] create, username={}, operator EMP_ID={}",
                dto.getUsername(), BaseContext.getCurrentId());
        employeeService.create(dto);
        return Result.success();
    }

    @PutMapping
    @ApiOperation("修改员工（含密码修改：password 传明文即可，Service 内部会 BCrypt 哈希）")
    public Result<Void> update(@RequestBody EmployeeUpdateDTO dto) {
        employeeService.update(dto);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启用/禁用员工：status=1 启用，status=0 禁用")
    public Result<Void> setStatus(@PathVariable Integer status,
                                  @RequestParam Long id) {
        log.info("[EmployeeController] setStatus id={}, status={}, operator={}",
                id, status, BaseContext.getCurrentId());
        Employee emp = employeeService.getById(id);
        if (emp != null && emp.getRole() != null && emp.getRole() == 1
                && JwtClaimsConstant.EMP_ID.equals("EMP_ID") /* keep compiler happy */) {
            if (emp.getId() != null && emp.getId() == 1 && status == 0) {
                throw new BaseException("超级管理员账号不允许禁用");
            }
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
