"""真实后端 API 链路 E2E：登录 → 积分过期扫描 → 商品 CRUD → 优惠券 → 下单 → 模拟支付
→ 发货/收货 → 评价 → 购物车/取消 → 售后/退款 → 幂等确认 → 登出。

仅依赖 stdlib。运行前需启动 MySQL/Redis 和 stellar-server（ci profile），并导入
stellar_mall_schema_current.sql、stellar_mall_init_data.sql、scripts/ci_seed.sql。

用法：BASE_URL=http://127.0.0.1:8082 python scripts/backend_e2e.py
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE_URL = os.environ.get("BASE_URL", "http://127.0.0.1:8082").rstrip("/")
USER_EMAIL = "ci-user@stellar.test"
USER_PASSWORD = "123456"
ADMIN_USER = "admin"
ADMIN_PASSWORD = "123456"


def call_raw(method: str, path: str, body: dict | None = None, token: str | None = None,
             header: str = "authentication") -> tuple[int, dict]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(BASE_URL + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header(header, token)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            status = response.status
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        status = exc.code
        raw = exc.read().decode("utf-8", "ignore")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        payload = {"code": status, "msg": raw}
    return status, payload


def call(method: str, path: str, body: dict | None = None, token: str | None = None,
         header: str = "authentication") -> object:
    status, payload = call_raw(method, path, body, token, header)
    if status != 200:
        raise AssertionError(f"{method} {path} HTTP {status}: {payload}")
    if payload.get("code") not in (1, 200):
        raise AssertionError(f"{method} {path} business error: {payload}")
    return payload.get("data")


def expect_business_error(method: str, path: str, body: dict | None = None,
                          token: str | None = None, header: str = "authentication") -> str:
    status, payload = call_raw(method, path, body, token, header)
    if status != 200 or payload.get("code") in (1, 200):
        raise AssertionError(f"{method} {path} 期望业务失败，实际 HTTP {status}: {payload}")
    return str(payload.get("msg") or "")


def expect_http_status(method: str, path: str, expected: int,
                       token: str | None = None, header: str = "authentication") -> dict:
    status, payload = call_raw(method, path, None, token, header)
    if status != expected:
        raise AssertionError(f"{method} {path} 期望 HTTP {expected}，实际 {status}: {payload}")
    return payload


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def money(value) -> float:
    return round(float(value or 0), 2)


def login_user() -> dict:
    return call("POST", "/user/user/login", {"email": USER_EMAIL, "password": USER_PASSWORD})


def login_admin() -> str:
    return call("POST", "/admin/employee/login",
                {"username": ADMIN_USER, "password": ADMIN_PASSWORD})["token"]


def pick_sku(user_token: str, index: int) -> dict:
    products = call("GET", "/user/spu/random?count=10", token=user_token)
    require(len(products) > index, "可售商品不足，初始化数据未导入")
    spu_id = products[index]["id"]
    spu = call("GET", f"/user/spu/{spu_id}", token=user_token)
    sku = next((s for s in spu.get("skus") or []
                if int(s.get("stock") or 0) > 0 and int(s.get("status", 1)) == 1), None)
    require(sku is not None, f"SPU {spu_id} 没有可售 SKU")
    return {
        "spu_id": spu_id,
        "sku_id": sku["id"],
        "name": spu.get("name"),
        "price": money(sku.get("price")),
        "stock": int(sku.get("stock")),
    }


def get_points(user_token: str) -> dict:
    return call("GET", "/user/points", token=user_token)


def get_wallet(user_token: str) -> dict:
    return call("GET", "/user/wallet", token=user_token)


def get_order(user_token: str, order_id) -> dict:
    return call("GET", f"/user/order/{order_id}", token=user_token)


def get_sku(user_token: str, spu_id, sku_id) -> dict:
    spu = call("GET", f"/user/spu/{spu_id}", token=user_token)
    sku = next((s for s in spu.get("skus") or [] if s.get("id") == sku_id), None)
    require(sku is not None, f"SPU {spu_id} 下找不到 SKU {sku_id}")
    return sku


def submit_direct(user_token: str, sku: dict, **extra) -> dict:
    body = {
        "address": "CI 自动化地址",
        "consignee": "CI 用户",
        "phone": "13800138000",
        "payMethod": 1,
        "clearCart": False,
        "items": [{"skuId": sku["sku_id"], "quantity": 1, "price": sku["price"]}],
    }
    body.update(extra)
    return call("POST", "/user/order/submit", body, token=user_token)


def main() -> int:
    login = login_user()
    user_token = login["token"]
    refresh_token = login["refreshToken"]
    admin_token = login_admin()
    print("[1/9] 用户 / 管理员登录 OK")

    # ---- 积分过期扫描：种子账户 5200，其中 200 已到期 ----
    expired = call("POST", "/admin/points/expire-scan", token=admin_token, header="token")
    require(int(expired) == 200, f"过期扫描数量异常: {expired}")
    points = get_points(user_token)
    require(int(points["availablePoints"]) == 5000,
            f"过期扫描后可用积分异常: {points}")
    records = call("GET", "/user/points/records?page=1&pageSize=20", token=user_token)
    require(any(r.get("bizType") == "EXPIRE" and int(r.get("points") or 0) == -200
                for r in records.get("records") or []), "过期积分流水缺失")
    print("[2/9] 积分过期扫描 OK（5200 → 5000，写入 EXPIRE 流水）")

    # ---- 管理端商品 CRUD ----
    spu_name = "CI E2E 商品 " + datetime.now().strftime("%H%M%S%f")
    created = call("POST", "/admin/spu", {
        "name": spu_name,
        "subtitle": "CI 自动化创建",
        "categoryId": 1,
        "status": 1,
        "skuList": [{
            "name": spu_name + " · 默认",
            "specs": "版本:CI",
            "price": 59.00,
            "originalPrice": 69.00,
            "stock": 5,
            "status": 1,
        }],
    }, token=admin_token, header="token")
    new_spu_id = created["id"]
    detail = call("GET", f"/admin/spu/{new_spu_id}", token=admin_token, header="token")
    require(detail.get("name") == spu_name, f"新建商品名称异常: {detail}")
    require(len(detail.get("skuList") or detail.get("skus") or []) == 1, f"新建商品 SKU 异常: {detail}")

    updated_name = spu_name + "（已更新）"
    call("PUT", "/admin/spu", {
        "id": new_spu_id,
        "name": updated_name,
        "categoryId": 1,
    }, token=admin_token, header="token")
    detail = call("GET", f"/admin/spu/{new_spu_id}", token=admin_token, header="token")
    require(detail.get("name") == updated_name, f"商品更新未生效: {detail.get('name')}")
    require(len(detail.get("skuList") or detail.get("skus") or []) == 1, "更新商品时 SKU 被误删")

    call("POST", f"/admin/spu/status/0?id={new_spu_id}", token=admin_token, header="token")
    detail = call("GET", f"/admin/spu/{new_spu_id}", token=admin_token, header="token")
    require(int(detail.get("status")) == 0, "下架未生效")
    call("POST", f"/admin/spu/status/1?id={new_spu_id}", token=admin_token, header="token")
    detail = call("GET", f"/admin/spu/{new_spu_id}", token=admin_token, header="token")
    require(int(detail.get("status")) == 1, "上架未生效")

    call("DELETE", f"/admin/spu/{new_spu_id}", token=admin_token, header="token")
    require(call("GET", f"/admin/spu/{new_spu_id}", token=admin_token, header="token") is None,
            "商品删除未生效")
    print("[3/9] 管理端商品创建/更新/上下架/删除 OK")

    # ---- 优惠券：后台创建 → 用户领取（限领 2） ----
    now = datetime.now()
    coupon_id = call("POST", "/admin/coupon", {
        "name": "CI 满 100 减 10",
        "type": 1,
        "conditionAmount": 100.00,
        "discountAmount": 10.00,
        "totalCount": 100,
        "perUserLimit": 2,
        "startTime": (now - timedelta(days=2)).strftime("%Y-%m-%d %H:%M:%S"),
        "endTime": (now + timedelta(days=3)).strftime("%Y-%m-%d %H:%M:%S"),
        "status": 1,
    }, token=admin_token, header="token")
    available = call("GET", "/user/coupon/available", token=user_token)
    require(any(c.get("id") == coupon_id for c in available), "可领取列表缺少新建优惠券")
    uc1 = call("POST", f"/user/coupon/claim/{coupon_id}", token=user_token)
    uc2 = call("POST", f"/user/coupon/claim/{coupon_id}", token=user_token)
    require(uc1 != uc2, "重复领取返回了同一张券")
    limit_msg = expect_business_error("POST", f"/user/coupon/claim/{coupon_id}", token=user_token)
    require("上限" in limit_msg, f"超限领取提示异常: {limit_msg}")
    print("[4/9] 优惠券创建/领取/限领拦截 OK")

    # ---- 订单 A：优惠券 + 积分抵扣 → 支付 → 发货 → 收货 → 评价 ----
    sku_a = pick_sku(user_token, 0)
    points_before_a = int(get_points(user_token)["availablePoints"])
    order_a = submit_direct(user_token, sku_a, userCouponId=uc1, usePoints=True,
                            pointsAmount=1.00)
    order_a_id = order_a["id"]
    require(int(order_a["pointsDeducted"]) == 100, f"积分抵扣数量异常: {order_a}")
    require(money(order_a["pointsAmount"]) == 1.00, f"积分抵扣金额异常: {order_a}")
    require(money(order_a["totalAmount"]) == sku_a["price"], f"订单总额异常: {order_a}")
    require(money(order_a["payAmount"]) == round(sku_a["price"] - 11.00, 2),
            f"优惠券/积分抵扣后应付异常: {order_a}")

    points = get_points(user_token)
    require(int(points["availablePoints"]) == points_before_a - 100, f"积分冻结后可用异常: {points}")
    require(int(points["frozenPoints"]) == 100, f"积分冻结数量异常: {points}")
    used = call("GET", "/user/coupon/my?status=2", token=user_token)
    require(any(c.get("id") == uc1 for c in used), "下单后优惠券未标记已使用")

    call("POST", f"/user/order/{order_a_id}/pay", {"payMethod": 1}, token=user_token)
    points = get_points(user_token)
    require(int(points["frozenPoints"]) == 0, f"支付后冻结积分未清零: {points}")

    call("POST", f"/admin/order/{order_a_id}/ship",
         {"trackingNo": "CI-A-001", "deliveryCompany": "CI 快递"},
         token=admin_token, header="token")
    call("POST", f"/user/order/{order_a_id}/confirm", token=user_token)
    order_a_detail = get_order(user_token, order_a_id)
    require(order_a_detail["status"] == "COMPLETED",
            f"收货后订单状态异常: {order_a_detail['status']}")
    item_a = order_a_detail["items"][0]

    review_id = call("POST", "/user/review", {
        "orderId": order_a_id,
        "orderNo": order_a_detail["orderNo"],
        "spuId": item_a["spuId"],
        "skuId": item_a["skuId"],
        "rating": 5,
        "content": "CI 自动化评价：物流快，商品符合预期。",
    }, token=user_token)
    duplicate_msg = expect_business_error("POST", "/user/review", {
        "orderId": order_a_id,
        "orderNo": order_a_detail["orderNo"],
        "spuId": item_a["spuId"],
        "skuId": item_a["skuId"],
        "rating": 5,
        "content": "重复评价",
    }, token=user_token)
    require("已评价" in duplicate_msg, f"重复评价拦截提示异常: {duplicate_msg}")
    reviews = call("GET", f"/user/review/spu/{item_a['spuId']}?page=1&pageSize=10",
                   token=user_token)
    require(any(r.get("id") == review_id for r in reviews.get("records") or []),
            "评价列表中找不到刚提交的评价")
    print("[5/9] 优惠券+积分下单/支付/发货/收货/评价/重复拦截 OK")

    # ---- 订单 B：购物车下单 → 取消（库存与冻结积分回滚） ----
    sku_c = pick_sku(user_token, 1)
    call("DELETE", "/user/cart/clear", token=user_token)
    call("POST", "/user/cart", {"skuId": sku_c["sku_id"], "qty": 2}, token=user_token)
    cart = call("GET", "/user/cart", token=user_token)
    require(len(cart) == 1 and int(cart[0]["qty"]) == 2, f"购物车添加/合并异常: {cart}")
    call("PUT", "/user/cart", {"id": cart[0]["id"], "qty": 1}, token=user_token)
    cart = call("GET", "/user/cart", token=user_token)
    require(int(cart[0]["qty"]) == 1, f"购物车数量更新异常: {cart}")

    points_before_c = int(get_points(user_token)["availablePoints"])
    stock_before_c = int(get_sku(user_token, sku_c["spu_id"], sku_c["sku_id"])["stock"])
    order_c = call("POST", "/user/order/submit", {
        "address": "CI 自动化地址",
        "consignee": "CI 用户",
        "phone": "13800138000",
        "usePoints": True,
        "pointsAmount": 1.00,
    }, token=user_token)
    order_c_id = order_c["id"]
    require(int(order_c["pointsDeducted"]) == 100, f"购物车下单积分抵扣异常: {order_c}")
    require(call("GET", "/user/cart", token=user_token) == [], "下单后购物车未清空")
    order_c_detail = get_order(user_token, order_c_id)
    require(len(order_c_detail["items"]) == 1 and int(order_c_detail["items"][0]["qty"]) == 1,
            f"购物车下单明细异常: {order_c_detail}")

    call("POST", f"/user/order/{order_c_id}/cancel", token=user_token)
    require(get_order(user_token, order_c_id)["status"] == "CANCELLED", "取消后订单状态异常")
    require(int(get_sku(user_token, sku_c["spu_id"], sku_c["sku_id"])["stock"]) == stock_before_c,
            "取消订单未回滚库存")
    points = get_points(user_token)
    require(int(points["availablePoints"]) == points_before_c, f"取消订单未解冻积分: {points}")
    require(int(points["frozenPoints"]) == 0, f"取消订单后冻结积分不为 0: {points}")
    print("[6/9] 购物车添加/改量/下单/清空 + 取消订单库存与积分回滚 OK")

    # ---- 订单 C：优惠券下单 → 仅退款售后 → 退款到钱包/退券 ----
    sku_b = pick_sku(user_token, 2)
    stock_before_b = int(get_sku(user_token, sku_b["spu_id"], sku_b["sku_id"])["stock"])
    wallet_before = money(get_wallet(user_token).get("balance"))
    order_b = submit_direct(user_token, sku_b, userCouponId=uc2)
    order_b_id = order_b["id"]
    require(money(order_b["payAmount"]) == round(sku_b["price"] - 10.00, 2),
            f"优惠券抵扣后应付异常: {order_b}")
    call("POST", f"/user/order/{order_b_id}/pay", {"payMethod": 1}, token=user_token)
    paid_b = get_order(user_token, order_b_id)

    aftersale = call("POST", "/user/aftersale", {
        "orderId": order_b_id,
        "skuId": sku_b["sku_id"],
        "type": 1,
        "reason": "CI 自动化仅退款测试",
    }, token=user_token)
    aftersale_id = aftersale["id"]
    call("POST", "/admin/aftersale/audit",
         {"id": aftersale_id, "approved": True, "remark": "CI 审核通过"},
         token=admin_token, header="token")
    call("POST", f"/admin/aftersale/{aftersale_id}/confirm-refund", token=admin_token, header="token")

    refunded = get_order(user_token, order_b_id)
    require(refunded["status"] == "REFUNDED", f"退款后订单状态异常: {refunded['status']}")
    detail = call("GET", f"/user/aftersale/{aftersale_id}", token=user_token)
    require(detail.get("status") == 5, f"售后单未完成: {detail.get('status')}")
    require(int(get_sku(user_token, sku_b["spu_id"], sku_b["sku_id"])["stock"]) == stock_before_b - 1,
            "仅退款不应回滚库存")
    wallet_after = money(get_wallet(user_token).get("balance"))
    require(wallet_after == round(wallet_before + money(paid_b["payAmount"]), 2),
            f"退款未按实付金额到账: before={wallet_before}, after={wallet_after}")
    returned = call("GET", "/user/coupon/my?status=1", token=user_token)
    require(any(c.get("id") == uc2 for c in returned), "整单退款后优惠券未退回")
    print("[7/9] 优惠券下单/仅退款/库存不回滚/钱包到账/优惠券退回 OK")

    # ---- 重复确认退款：幂等，不二次入账 ----
    call("POST", f"/admin/aftersale/{aftersale_id}/confirm-refund", token=admin_token, header="token")
    require(money(get_wallet(user_token).get("balance")) == wallet_after,
            "重复确认退款导致钱包重复入账")
    require(int(get_sku(user_token, sku_b["spu_id"], sku_b["sku_id"])["stock"]) == stock_before_b - 1,
            "重复确认退款导致库存再次变化")
    print("[8/9] 重复确认退款幂等 OK")

    # ---- 登出：refresh token 与 access token 同时失效 ----
    call("POST", "/user/user/logout", {"refreshToken": refresh_token}, token=user_token)
    refresh_msg = expect_business_error("POST", "/user/user/refresh",
                                        {"refreshToken": refresh_token})
    require("无效" in refresh_msg or "过期" in refresh_msg,
            f"登出后 refresh token 仍可用: {refresh_msg}")
    expect_http_status("GET", "/user/user/me", 401, token=user_token)
    print("[9/9] 登出后 refresh 失效、access 进入黑名单 OK")

    print("[完成] 全部场景通过")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"BACKEND_E2E_FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
