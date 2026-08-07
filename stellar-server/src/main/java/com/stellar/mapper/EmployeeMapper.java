package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Employee;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 员工/管理员 Mapper。表：stellar_employee。
 */
@Mapper
public interface EmployeeMapper {

    /** 按用户名查员工（登录校验用） */
    @Select("SELECT * FROM stellar_employee WHERE username = #{username} LIMIT 1")
    Employee getByUsername(String username);

    /** 按 ID 查员工（回显/信息查询用） */
    @Select("SELECT * FROM stellar_employee WHERE id = #{id} LIMIT 1")
    Employee getById(Long id);

    /** 新增员工（含密码哈希、公共 4 字段自动注入） */
    @AutoFill(OperationType.INSERT)
    int insert(Employee employee);

    /** 修改员工（公共 update 字段自动注入，可部分字段更新——MyBatis XML 动态 SQL） */
    @AutoFill(OperationType.UPDATE)
    int update(Employee employee);

    /** 分页查询总条数 */
    Long count(@Param("name") String name,
               @Param("status") Integer status,
               @Param("role") Integer role);

    /** 分页查询记录 */
    List<Employee> page(@Param("name") String name,
                        @Param("status") Integer status,
                        @Param("role") Integer role,
                        @Param("offset") int offset,
                        @Param("pageSize") int pageSize);
}
