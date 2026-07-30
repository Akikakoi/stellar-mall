package com.stellar.exception;

/** JWT 无效 / 过期 / 被篡改 */
public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String msg) { super(msg, 40101); }
}
