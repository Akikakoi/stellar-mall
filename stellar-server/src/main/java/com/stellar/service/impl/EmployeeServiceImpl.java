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
import com.stellar.utils.JwtUtil;
import com.stellar.vo.EmployeeLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmployeeServiceImpl implements EmployeeService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private final EmployeeMapper employeeMapper;
    private final JwtProperties jwtProperties;

    @Autowired
    public EmployeeServiceImpl(EmployeeMapper employeeMapper, JwtProperties jwtProperties) {
        this.employeeMapper = employeeMapper;
        this.jwtProperties = jwtProperties;
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
        Employee emp = employeeMapper.getByUsername(dto.getUsername());
        if (emp == null) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        if (!BCRYPT.matches(dto.getPassword(), emp.getPasswordHash())) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }
        if (emp.getStatus() != null && emp.getStatus().equals(StatusConstant.DISABLE)) {
            throw new LoginFailedException(MessageConstant.ACCOUNT_LOCKED);
        }

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

        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );
        log.info("[EmployeeService] login OK: username={}, EMP_ID={}, role={}", dto.getUsername(), emp.getId(), roleStr);

        return EmployeeLoginVO.builder()
                .id(emp.getId())
                .userName(emp.getUsername())
                .name(emp.getName())
                .role(emp.getRole())
                .token(token)
                .build();
    }

    @Override
    public PageResult page(String name, Integer status, Integer role, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        Long total = employeeMapper.count(name, status, role);
        List<Employee> list = employeeMapper.page(name, status, role, offset, pageSize);
        // ⚠️ 返回给前端时把 password_hash 清空，避免泄漏
        list.forEach(e -> e.setPasswordHash(null));
        return new PageResult(total, list);
    }

    @Override
    public Employee getById(Long id) {
        Employee e = employeeMapper.getById(id);
        if (e != null) e.setPasswordHash(null);
        return e;
    }

    @Override
    public void setStatus(Long id, Integer status) {
        Employee upd = Employee.builder()
                .id(id)
                .status(status)
                .build();
        // ⚠️ BaseContext.getCurrentId() 已由 JwtTokenAdminInterceptor.preHandle 写入
        employeeMapper.update(upd);
    }

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
