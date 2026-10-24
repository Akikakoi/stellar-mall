# 006 — 手写曲线收敛到缓动 token

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: MEDIUM
- **Category**: 7 一致性
- **Estimated scope**: 3 文件 / 4 处

## Problem

仓库定义了 `--ease-out` 与 `--ease-in-out` 两个强曲线（`main.scss:27-28`），但仍有 4 处手写近似曲线。结果是"同一个滑动指示器"在顶栏和聊天页表现略有差异，且后续改曲线要满仓搜 `cubic-bezier`。

`frontend/src/components/NavHeader.vue:492-494`
```css
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              width 0.3s cubic-bezier(0.4, 0, 0.2, 1),
              opacity 0.2s ease;
```

`frontend/src/components/ChatMessage.vue:416`
```css
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
```

`frontend/src/assets/main.scss:204`
```css
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1) !important;
```

`frontend/src/assets/main.scss:541`
```css
  animation: glass-dialog-in 0.25s cubic-bezier(0.2, 0.8, 0.2, 1) both;
```

## Target

- 屏幕内位移/形态变化（导航指示器、来源箭头）→ `var(--ease-in-out)`（`cubic-bezier(0.77, 0, 0.175, 1)`）。
- 入场（玻璃弹窗）→ `var(--ease-out)`（`cubic-bezier(0.23, 1, 0.32, 1)`）。
- 主题切换的统一时长改写 → `var(--ease-in-out)`。

```css
/* NavHeader.vue:492-494 */
  transition: transform 0.3s var(--ease-in-out),
              width 0.3s var(--ease-in-out),
              opacity 0.2s ease;
```
```css
/* ChatMessage.vue:416 */
  transition: transform 0.25s var(--ease-in-out);
```
```css
/* main.scss:204 */
  transition-timing-function: var(--ease-in-out) !important;
```
```css
/* main.scss:541 */
  animation: glass-dialog-in 0.25s var(--ease-out) both;
```

## Repo conventions to follow

- token 定义位置：`main.scss:27-28`；`--ease-in-out` 的注释写明"屏幕内位移/形态变化用"。
- `var()` 在 `!important` 声明中同样可用，不要为此改优先级。

## Steps

1. 修改 `frontend/src/components/NavHeader.vue:492` 与 `:493`，两处 `cubic-bezier(0.4, 0, 0.2, 1)` 替换为 `var(--ease-in-out)`。
2. 修改 `frontend/src/components/ChatMessage.vue:416` 同上。
3. 修改 `frontend/src/assets/main.scss:204`，替换为 `var(--ease-in-out)`。
4. 修改 `frontend/src/assets/main.scss:541`，替换为 `var(--ease-out)`（这是入场，不是屏内位移）。

## Boundaries

- 不要改任何时长（0.3s / 0.25s 保持不变）。
- 不要把 NavHeader 指示器的 `width` 过渡一起删掉——那是另一个待评估项，本方案只换曲线。
- 不要动 `main.scss:29-30` 的 `--transition-*` 定义。

## Verification

- **Mechanical**: `cd frontend && npm run build`；全仓 `grep -rn "cubic-bezier(0.4, 0, 0.2, 1)" frontend/src` 应无结果。
- **Feel check**:
  - 顶栏在"首页/商城/分类"之间切换，下划线指示器滑动：应比之前更快到达、末端更稳（强 ease-in-out）。
  - 展开/收起 AI 回复里的来源列表，箭头翻转与之前无异但更干脆。
  - 切换明暗主题：全站颜色应仍同步渐变，无局部闪烁。
  - 打开玻璃弹窗：入场缩放不应有"先慢后快"的顿感。
- **Done when**: 4 处手写曲线消失，观感差异仅在节奏更跟手。
