# 005 — 弹窗离场改用 ease-out

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: MEDIUM
- **Category**: 2 缓动与时长
- **Estimated scope**: 1 文件 / 4 行

## Problem

全站弹窗（含玻璃弹窗）的**离场**动画用了 `ease-in`。`ease-in` 起步慢：用户点"关闭"之后，前 100ms 画面几乎没有变化，整个关闭动作显得迟钝、不跟手。项目已有强 ease-out token，却没有用在离场上。

`frontend/src/assets/main.scss:522-551`（节选，四个 `ease-in` 位置）
```css
body .dialog-fade-leave-active {
  animation: dialog-pure-fade-out 0.2s ease-in both;
}
body .dialog-fade-leave-active .el-overlay-dialog {
  animation: dialog-pure-fade-out 0.2s ease-in both;
}
body .dialog-fade-leave-active .el-overlay.glass-overlay .el-overlay-dialog {
  animation: glass-dialog-out 0.2s ease-in both;
}
body .dialog-fade-leave-active .el-overlay.glass-overlay {
  animation: dialog-pure-fade-out 0.2s ease-in both;
}
```

## Target

四处离场统一 `var(--ease-out)`（`cubic-bezier(0.23, 1, 0.32, 1)`，定义于 `main.scss:27`），时长保持 0.2s 不变：

```css
body .dialog-fade-leave-active {
  animation: dialog-pure-fade-out 0.2s var(--ease-out) both;
}
body .dialog-fade-leave-active .el-overlay-dialog {
  animation: dialog-pure-fade-out 0.2s var(--ease-out) both;
}
body .dialog-fade-leave-active .el-overlay.glass-overlay .el-overlay-dialog {
  animation: glass-dialog-out 0.2s var(--ease-out) both;
}
body .dialog-fade-leave-active .el-overlay.glass-overlay {
  animation: dialog-pure-fade-out 0.2s var(--ease-out) both;
}
```

## Repo conventions to follow

- 入场已经是 `0.25s ease-out`（`main.scss:523`），离场与之对称即可，不要改时长。
- 曲线一律走 token，禁止再手写 cubic-bezier。

## Steps

1. `frontend/src/assets/main.scss:526` 把 `ease-in` 改为 `var(--ease-out)`。
2. 同文件 `:533` 同上。
3. 同文件 `:547` 同上。
4. 同文件 `:550` 同上。
5. 检查同文件 `:541` 的入场曲线属于方案 006，不在本方案内。

## Boundaries

- 只改这 4 处 timing-function；不要动 `both`、时长、keyframes 定义。
- 不要动 `main.scss:207-222` 的 reduced-motion 兜底。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - C 端打开商品管理类弹窗（如新增地址）、后台打开任意编辑弹窗，点关闭：**第一帧就应开始变化**，不得有 ~100ms 的"呆住"。
  - DevTools Animations 播放速度 10%，对比入场与离场：两者都应是"起步快、收尾缓"。
  - 玻璃弹窗（`glass-dialog`）离场时磨砂不应闪回为透明。
- **Done when**: 四处离场 animation 的 timing-function 均为 `var(--ease-out)`。
