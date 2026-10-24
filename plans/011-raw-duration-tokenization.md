# 011 — 裸时长收敛到过渡 token

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: LOW
- **Category**: 7 一致性
- **Estimated scope**: 3 文件 / 16 行

## Problem

首页、商城、顶栏里散落着 `.15s / .2s / .25s` 的裸时长，且大多使用默认 `ease`。全局 `body *` 已经用 `--transition-color`（160ms ease）统一了颜色过渡，这些局部声明会把它覆盖成不同时长——同一个 hover 在首页是 250ms、在商品卡是 200ms、在顶栏是 150ms。用户说不清哪里不对，但会觉得"不齐"。

`frontend/src/views/user/Home.vue:739`
```css
  transition: color .2s, border-color .2s; user-select: none;
```
`frontend/src/views/user/Home.vue:748`
```css
  transition: background .25s, color .25s, box-shadow .25s;
```
`frontend/src/views/user/Home.vue:756`
```css
  transition: transform .25s;
```
`frontend/src/views/user/Home.vue:799`
```css
  transition: transform .25s, box-shadow .25s; overflow: hidden;
```
`frontend/src/views/user/Home.vue:822`
```css
  transition: color .2s;
```
`frontend/src/views/user/Home.vue:824`
```css
  transition: transform .2s;
```
`frontend/src/views/user/Shop.vue:764`
```css
  transition: color 0.15s;
```
`frontend/src/views/user/Shop.vue:791`
```css
  transition: background 0.15s, color 0.15s, transform 0.15s;
```
`frontend/src/views/user/Shop.vue:815`
```css
  transition: border-color 0.2s, box-shadow 0.2s;
```
`frontend/src/views/user/Shop.vue:861`
```css
  transition: background 0.15s, color 0.15s;
```
`frontend/src/views/user/Shop.vue:883`
```css
  transition: background 0.2s, opacity 0.2s;
```
`frontend/src/components/NavHeader.vue:504`
```css
  transition: color 0.25s ease;
```
`frontend/src/components/NavHeader.vue:516`
```css
  transition: color 0.25s ease, transform 0.25s ease;
```
`frontend/src/components/NavHeader.vue:618`
```css
  transition: color 0.15s;
```
`frontend/src/components/NavHeader.vue:642`
```css
  transition: background 0.15s, color 0.15s, transform 0.15s;
```
`frontend/src/components/NavHeader.vue:653`
```css
  transition: color 0.2s;
```
`frontend/src/components/NavHeader.vue:722`
```css
  transition: background 0.15s, color 0.15s;
```

## Target

映射规则（两个 token 见 `main.scss:29-30`）：

- 颜色/背景/描边/阴影/透明度 → `var(--transition-color)`（160ms ease）
- 位移/缩放 → `var(--transition-base)`（200ms ease-out）

逐行替换表：

| 位置 | 目标值 |
| --- | --- |
| Home.vue:739 | `transition: color var(--transition-color), border-color var(--transition-color); user-select: none;` |
| Home.vue:748 | `transition: background var(--transition-color), color var(--transition-color), box-shadow var(--transition-color);` |
| Home.vue:756 | `transition: transform var(--transition-base);` |
| Home.vue:799 | `transition: transform var(--transition-base), box-shadow var(--transition-color); overflow: hidden;` |
| Home.vue:822 | `transition: color var(--transition-color);` |
| Home.vue:824 | `transition: transform var(--transition-base);` |
| Shop.vue:764 | `transition: color var(--transition-color);` |
| Shop.vue:791 | `transition: background var(--transition-color), color var(--transition-color), transform var(--transition-base);` |
| Shop.vue:815 | `transition: border-color var(--transition-color), box-shadow var(--transition-color);` |
| Shop.vue:861 | `transition: background var(--transition-color), color var(--transition-color);` |
| Shop.vue:883 | `transition: background var(--transition-color), opacity var(--transition-color);` |
| NavHeader.vue:504 | `transition: color var(--transition-color);` |
| NavHeader.vue:516 | `transition: color var(--transition-color), transform var(--transition-base);` |
| NavHeader.vue:618 | `transition: color var(--transition-color);` |
| NavHeader.vue:642 | `transition: background var(--transition-color), color var(--transition-color), transform var(--transition-base);` |
| NavHeader.vue:653 | `transition: color var(--transition-color);` |
| NavHeader.vue:722 | `transition: background var(--transition-color), color var(--transition-color);` |

## Repo conventions to follow

- 已正确的范例（照抄这种写法）：`Home.vue:843`、`:857`、`:872`；`Dashboard.vue:332`。
- 不要改 `--transition-base/--transition-color` 的定义值。

## Steps

按上表逐行替换，一次一个文件，替换完立即保存。

## Boundaries

- 只动表中列出的 17 行；其它 `.15s/.2s/.25s`（尤其是 `SpuDetail` 的轮播、`Login` 的 `mode-fade`）留给后续评估，不要顺手全改。
- 不要改任何 hover 的目标值、位移距离。
- 不要删除全局 `main.scss:184-194` 的 `body *` 颜色过渡。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - 首页依次划过：分类小 tab、查看更多、商品卡、收藏心形、领券卡——颜色反馈节奏应一致，"更多"箭头的位移应略慢于颜色变化。
  - 顶栏导航 hover 与商城筛选 hover 对比，不应再出现一处"黏"一处"急"。
  - 主题切换：全站颜色仍同步（`theme-transitioning` 会统一改写为 350ms）。
- **Done when**: 表内 17 行的 transition 全部使用 token，全局不残留裸时长（这三处文件内）。
