# 012 — AI 助手品牌图标去掉回弹旋转

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: LOW
- **Category**: 7 一致性
- **Estimated scope**: 1 文件 / 2 处

## Problem

AI 助手侧栏的品牌图标 hover 时会放大 1.05 并旋转 -5°，同时使用了带过冲的 `cubic-bezier(0.34, 1.56, 0.64, 1)`（回弹曲线）。全站其它动效都克制在强 ease-out / ease-in-out，这一处是唯一"弹一下"的元素，显得风格断层；而且旋转会让 Logo 看起来像在"摇晃"，对一个可点击回主页的入口没有信息价值。该 hover 也没有 `(hover: hover)` 门控。

`frontend/src/views/user/RagChat.vue:682-699`
```scss
  &:hover .brand-icon {
    transform: scale(1.05) rotate(-5deg);
  }
}

.brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-active));
  color: #fff;
  font-size: 20px;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: 0 4px 12px rgba(0, 113, 227, 0.3);
}
```

## Target

```scss
/* 品牌图标 hover：只做轻微放大，不做旋转/回弹，与全站克制调性一致 */
@media (hover: hover) {
  .brand:hover .brand-icon {
    transform: scale(1.03);
  }
}
```
```scss
.brand-icon {
  /* …其余不变… */
  transition: transform var(--transition-base);
  /* … */
}
```

具体做法：把 `:682-684` 的 `&:hover .brand-icon` 块从 `.brand` 内移出，改写为上面的同级媒体查询；`:697` 的 transition 换成 `var(--transition-base)`（200ms，强 ease-out）。

## Repo conventions to follow

- hover 位移必须 `@media (hover: hover)` 门控（范例 `Home.vue:847-850`）——本项与方案 001 同一原则，但作用对象不同，故单独成案。
- 位移时长用 `--transition-base`；禁止过冲曲线（`1.56` 这类 >1 的控制点）。

## Steps

1. 删除 `frontend/src/views/user/RagChat.vue:682-684` 的 `&:hover .brand-icon { transform: scale(1.05) rotate(-5deg); }`。
2. 在 `.brand` 规则外部（紧邻其后）加入 `@media (hover: hover) { .brand:hover .brand-icon { transform: scale(1.03); } }`。
3. `frontend/src/views/user/RagChat.vue:697`：`transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);` → `transition: transform var(--transition-base);`。

## Boundaries

- 不要改图标尺寸、渐变、阴影。
- 不要动同文件 `.prompt:hover`（已由方案 001 处理）。
- 不要给 Logo 增加点击时的旋转/弹跳"彩蛋"。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - 鼠标划过 AI 助手左上角 Logo：应只有 200ms 的轻微放大，无旋转、无回弹过冲。
  - 触屏模拟点按 Logo：不应停留在放大态。
  - DevTools Animations 10% 速度：确认没有越过 1.0 再回来的过冲。
- **Done when**: 该 hover 只保留 `scale(1.03)`，过渡走 `--transition-base`，且触屏不触发。
