# 002 — 给点击目标补按压反馈

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: HIGH
- **Category**: 3 物理性与原点
- **Estimated scope**: 1 文件主改（`main.scss`）+ 1 行过渡补全

## Problem

全站只有两处有 `:active` 位移：`frontend/src/components/NavHeader.vue:648`（`.nav-history-tag:active { transform: scale(0.97); }`）和 `frontend/src/views/user/Shop.vue:797`。其余所有可点击目标——商品卡、收藏心形、加购/立即购买按钮、AI 提示卡、后台快捷操作、浮动侧栏——**按下时没有任何视觉确认**。触屏没有 hover，按下的这 100ms 是用户唯一能确认"点到了"的时刻；目前是完全静默的。

`frontend/src/assets/main.scss:425-434`（现状：只有鼠标抬起后的 hover 位移）
```css
.hover-lift {
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}
/* hover 位移用 (hover: hover) 门控：触屏点按不再误触发抬升 */
@media (hover: hover) {
  .hover-lift:hover {
    transform: var(--hover-lift);
    box-shadow: var(--shadow-md);
  }
}
```

`frontend/src/components/FloatingSidebar.vue:116-127`（没有 transform 过渡，按下会瞬跳）
```css
.sidebar-item {
  /* … */
  transition: background var(--transition-color), color var(--transition-color);
  min-width: 60px;
}
```

## Target

在 `frontend/src/assets/main.scss` 的 `.hover-lift` 工具类之后追加一段全局按压反馈：

```css
/* ===== 按压反馈（:active）=====
   按下时轻微下沉：按钮 0.97，可点击卡片 0.98 并落回表面（不再保持 hover 抬升）。
   160ms + 强 ease-out，松开时走元素自身的 transform 过渡回弹。 */
.el-button:not(.is-disabled):not(.is-link):not(.is-text) {
  transition: transform 160ms var(--ease-out);
}
.el-button:not(.is-disabled):not(.is-link):not(.is-text):active {
  transform: scale(0.97);
}

.spu-card:active,
.more-card:active,
.coupon-entry-card:active,
.coupon-card:active,
.fav-card:active,
.p-card:active,
.action-item:active,
.sidebar-item:active,
.fav-btn:active {
  transform: translateY(0) scale(0.98);
}
```

并在 `FloatingSidebar.vue:126` 的过渡列表末尾补 `, transform 160ms var(--ease-out)`，让浮动侧栏按下不瞬跳。

## Repo conventions to follow

- 时长与曲线必须用 token：`--ease-out` = `cubic-bezier(0.23, 1, 0.32, 1)`，定义在 `main.scss:27`；按下反馈预算 100–160ms。
- 已存在的按压范例：`NavHeader.vue:648`、`Shop.vue:797`（`scale(0.97)`）。

## Steps

1. 在 `frontend/src/assets/main.scss` 的 `.hover-lift` 块（`:425-434`）之后插入上面两段规则，注释保留。
2. 修改 `frontend/src/components/FloatingSidebar.vue:126`：
   ```css
   transition: background var(--transition-color), color var(--transition-color), transform 160ms var(--ease-out);
   ```
3. 确认 `.coupon-card` 与 `.fav-card` 已有 transform 过渡（`Coupons.vue:175`、`Favorites.vue:156`），无需再改。

## Boundaries

- 不要给 `<a>` 链接、导航文字、表格行加按压位移。
- 不要改 hover 的位移数值与曲线。
- 不要动 `.el-button.is-link`（纯文字按钮按下下沉会显得廉价，已在选择器中排除）。
- 不新增依赖。

## Verification

- **Mechanical**: `cd frontend && npm run build`（应成功）。
- **Feel check**:
  - 桌面：按住"加入购物车"不松手，按钮应略微缩小并保持；鼠标拖出按钮再松开，应立即回弹，不得卡在缩小态。
  - 触屏模拟：点击首页商品卡，应看到 160ms 内的下沉回弹；长按也不应触发 hover 抬升。
  - DevTools Animations 面板播放速度调到 10%，确认按下与松开是两条独立的短过渡，而不是一次性动画。
  - 开启 `prefers-reduced-motion: reduce`（Rendering 面板），按下不应再有位移（全局兜底会把 transform 过渡收窄掉），但颜色反馈保留。
- **Done when**: 全站主要按钮/卡片按下均有 160ms 下沉反馈，且不破坏既有 hover 抬升。
