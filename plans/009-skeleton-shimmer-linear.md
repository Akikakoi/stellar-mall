# 009 — 骨架屏 shimmer 补 linear

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: LOW
- **Category**: 7 一致性
- **Estimated scope**: 1 文件 / 2 行

## Problem

全局骨架屏的 shimmer 明确用 `linear` 并写了原因（恒定运动不能有加减速，否则每循环不自然地"呼吸"）。商城的骨架屏复制了高光渐变，却漏写 `linear`——实际按默认 `ease` 播放，于是商城首屏加载时骨架块会脉动，与其它页面的骨架屏不一致。

`frontend/src/assets/main.scss:435-441`（正确范例）
```css
.skeleton {
  background: linear-gradient(90deg, var(--bg-hover) 25%, var(--bg-card) 50%, var(--bg-hover) 75%);
  background-size: 200% 100%;
  /* shimmer 属恒定运动，用 linear，避免每循环不自然的加减速脉动 */
  animation: shimmer 1.5s linear infinite;
  border-radius: var(--radius-sm);
}
```

`frontend/src/views/user/Shop.vue:1114-1126`（现状）
```css
.skeleton-box {
  background: linear-gradient(90deg, var(--bg-hover, #f0f0f0) 25%, var(--bg-card, #e8e8e8) 50%, var(--bg-hover, #f0f0f0) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}
.skeleton-line {
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--bg-hover, #f0f0f0) 25%, var(--bg-card, #e8e8e8) 50%, var(--bg-hover, #f0f0f0) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  margin-bottom: 10px;
}
```

## Target

两处动画都补 `linear`：

```css
  animation: skeleton-shimmer 1.5s linear infinite;
```

## Repo conventions to follow

- 恒定运动（shimmer、进度条、marquee）一律 `linear`，见 `main.scss:438` 的既有注释。
- 不要顺手把 `@keyframes skeleton-shimmer` 合并进全局 `shimmer`——两者背景位移方向相反（`200% → -200%` vs `-200% → 200%`），合并会改变视觉方向。

## Steps

1. `frontend/src/views/user/Shop.vue:1117`：`animation: skeleton-shimmer 1.5s infinite;` → `animation: skeleton-shimmer 1.5s linear infinite;`。
2. 同文件 `:1124` 同上。

## Boundaries

- 只加 timing-function，不改 1.5s 时长、不改渐变颜色、不改 keyframes。
- 不要动 `.spu-card--skeleton:hover`（`:1113`）。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - 商城页冷刷新（Network 限速 Slow 3G），观察骨架卡：高光应匀速扫过，**不应看到明暗起伏**。
  - 与首页/其它使用 `.skeleton` 的页面并排对比，节奏应一致。
- **Done when**: 两处 shimmer 的 timing-function 均为 `linear`。
