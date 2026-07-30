package com.stellar.exception;

/** 登录失败（用户名/密码错误/账号锁定） */
public class LoginFailedException extends BaseException {
    public LoginFailedException(String msg) { super(msg, 40101); }
}
