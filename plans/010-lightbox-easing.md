# 010 — 图片放大预览的入场缓动与物理感

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: LOW
- **Category**: 2 缓动 / 3 物理性
- **Estimated scope**: 1 文件 / 2 处

## Problem

商品详情点击主图进入放大预览时，遮罩用裸 `ease` 播放淡入（默认缓动，起步偏慢）；切图的图片只有 `opacity` 淡入，没有任何位移/缩放，看起来像"凭空闪现"，缺少从缩略图放大的空间感。

`frontend/src/views/user/SpuDetail.vue:689-703`
```css
.zoom-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  outline: none;
  animation: zoomFadeIn 0.2s ease;
}
@keyframes zoomFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

`frontend/src/views/user/SpuDetail.vue:747-752`
```css
  /* 切图时随 :key 重挂载重放 180ms 淡入，替代硬跳变 */
  animation: zoom-img-in 0.18s var(--ease-out);
}

@keyframes zoom-img-in {
  from { opacity: 0; }
}
```

## Target

- 遮罩：只换缓动（全屏黑色遮罩**不要**做缩放，否则会露出边缘）。
- 图片：入场加 `scale(0.98)`，从接近终态放大到位，符合"不凭空出现"。

```css
  animation: zoomFadeIn 0.2s var(--ease-out);
```
```css
@keyframes zoom-img-in {
  from { opacity: 0; transform: scale(0.98); }
}
```

## Repo conventions to follow

- 入场用 `--ease-out`（`main.scss:27`）。
- 禁止 `scale(0)`；起点取 0.9–0.97 区间，本处取 0.98（放大预览本身接近全屏，过大的缩放会显得夸张）。
- 模态/全屏层 centering 不动，`transform-origin` 保持默认 center（全屏预览没有触发器锚点，center 正确，不要改）。

## Steps

1. `frontend/src/views/user/SpuDetail.vue:698`：`animation: zoomFadeIn 0.2s ease;` → `animation: zoomFadeIn 0.2s var(--ease-out);`。
2. `frontend/src/views/user/SpuDetail.vue:750-752`：给 `from` 增加 `transform: scale(0.98);`。

## Boundaries

- 不要动 `.zoom-overlay` 的布局、层级、背景色。
- 不要给遮罩加缩放。
- 不要改图片 0.18s 时长与样式 `object-fit`。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - 打开商品详情，点击主图：遮罩应立即开始变暗（无起步迟滞），大图从 98% 放大到 100% 并淡入。
  - 在预览内左右切图：每张图应重放一次 180ms 的缩放淡入，不应出现硬跳。
  - DevTools Reduced Motion 开启：图片应瞬时出现且不缩放。
- **Done when**: 遮罩走 `var(--ease-out)`，图片入场带 0.98→1 缩放。
