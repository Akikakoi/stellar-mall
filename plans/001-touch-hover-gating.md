# 001 — 触屏 hover 位移补上门控

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: HIGH
- **Category**: 6 无障碍
- **Estimated scope**: 4 文件 / 6 处，仅 CSS 包裹

## Problem

项目里 hover 位移的既有约定是用 `@media (hover: hover)` 门控（见 `frontend/src/assets/main.scss:429-434` 的 `.hover-lift`），全仓 14 处已遵守。但下列 6 处遗漏：触屏点按会触发 hover 状态且**松手后不回退**，卡片会一直保持抬升/放大，看起来像卡住了。

`frontend/src/views/user/Home.vue:820`
```css
.more-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); border-color: var(--brand-primary); }
```

`frontend/src/views/user/Home.vue:859`
```css
.fav-btn:hover { background: rgba(0,0,0,0.55); transform: scale(1.15); }
```

`frontend/src/views/user/Home.vue:874`
```css
.coupon-entry-card:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(255,140,0,0.15); }
```

`frontend/src/views/user/Shop.vue:1060`
```css
.fav-btn:hover { background: rgba(0,0,0,0.55); transform: scale(1.15); }
```

`frontend/src/views/user/RagChat.vue:1116-1126`（嵌套在 `.prompt { … }` 内）
```scss
  &:hover {
    background: var(--glass-hover);
    border-color: var(--brand-primary);
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 113, 227, 0.12);

    .prompt-arrow {
      transform: translateX(4px);
      color: var(--brand-primary);
    }
  }
```

`frontend/src/views/admin/Dashboard.vue:382`
```css
.action-item:hover { background: var(--brand-primary-soft); border-color: var(--brand-primary-border); transform: var(--hover-lift); }
```

## Target

每处 hover 规则整体包进 `@media (hover: hover)`（与仓库范例一致：`Home.vue:847-850` 的 `.spu-card` 就是这样写的）。颜色与位移一起门控，避免触屏出现“背景变了但没抬升”的半状态。

以 Home.vue 为例：
```css
@media (hover: hover) {
  .more-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), inset 0 1px 0 var(--glass-highlight); border-color: var(--brand-primary); }
}
```

RagChat.vue 是 SCSS 嵌套，改成同级的媒体查询块（放在 `.prompt` 规则之后）：
```scss
@media (hover: hover) {
  .prompt:hover {
    background: var(--glass-hover);
    border-color: var(--brand-primary);
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 113, 227, 0.12);
  }
  .prompt:hover .prompt-arrow {
    transform: translateX(4px);
    color: var(--brand-primary);
  }
}
```

## Repo conventions to follow

- 门控写法范例：`frontend/src/views/user/Home.vue:777-779`、`:801-803`、`:847-850`；`frontend/src/assets/main.scss:429-434`。
- 只包裹，不改任何数值、不改选择器优先级。

## Steps

1. `frontend/src/views/user/Home.vue:820` 的 `.more-card:hover` 包进 `@media (hover: hover) { }`。
2. 同文件 `:859` 的 `.fav-btn:hover` 同上。
3. 同文件 `:874` 的 `.coupon-entry-card:hover` 同上；同时把紧随其后的 `.more-card:hover .more-txt`（`:825`）与 `.more-card:hover .more-arrow`（`:826`）保留在原处不动（不动位移，无触屏问题）。
4. `frontend/src/views/user/Shop.vue:1060` 的 `.fav-btn:hover` 包进媒体查询。
5. `frontend/src/views/user/RagChat.vue` 把 `.prompt` 内的 `&:hover { … }` 整块移出，改写为上面的同级 `@media (hover: hover)` 块；`.prompt-arrow` 的位移规则同样放入。
6. `frontend/src/views/admin/Dashboard.vue:382` 的 `.action-item:hover` 包进媒体查询。

## Boundaries

- 只动上述 6 处选择器；不要顺手给其它 hover 加门控。
- 不改变位移数值、时长、曲线。
- 不新增依赖，不修改模板结构。

## Verification

- **Mechanical**: `cd frontend && npm run typecheck`（应通过；纯 CSS 改动不应影响类型）。
- **Feel check**:
  - DevTools 设备模拟开启触屏（Toggle device toolbar），点按首页商品卡/收藏心形/"查看更多"卡、商城收藏心形、AI 助手提示卡、后台快捷操作：**松手后不得保持抬升或放大**。
  - 退出触屏模拟，用鼠标 hover：抬升/缩放与改动前完全一致。
- **Done when**: 6 处 hover 位移都只在 `(hover: hover)` 下生效，桌面观感无变化。
