package com.stellar.service;

import com.stellar.dto.MallUserLoginDTO;
import com.stellar.dto.MallUserProfileUpdateDTO;
import com.stellar.entity.MallUser;
import com.stellar.vo.MallUserLoginVO;
import com.stellar.vo.MallUserVO;

public interface MallUserService {

    /**
     * C 端用户邮箱 + 密码登录。返回 userId + token。
     * 校验失败抛 LoginFailedException / AccountLockedException。
     */
    MallUserLoginVO login(MallUserLoginDTO dto);

    /** 根据 id 查询用户（已锁定的也返回，由调用方判断）。 */
    MallUser getById(Long id);

    /** 获取用户资料（返回 VO）。 */
    MallUserVO getProfile(Long id);

    /** 更新用户资料（nickname 等可选字段，传哪个改哪个）。 */
    void updateProfile(Long id, MallUserProfileUpdateDTO dto);

    /** 邮箱验证码登录或注册（无需密码，由邮箱验证码校验后调用）。 */
    MallUserLoginVO loginOrRegisterByEmail(String email);

    /** 注销当前账号（将账号状态置为已注销）。 */
    void deactivateAccount(Long id);
}