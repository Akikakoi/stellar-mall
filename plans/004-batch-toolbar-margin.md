# 004 — 批量操作栏不再动画 margin-bottom

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: MEDIUM
- **Category**: 5 性能
- **Estimated scope**: 1 文件 / 2 行

## Problem

商品管理的批量操作栏进场时同时动画 `margin-bottom`。这是在动画一个布局属性：批量栏出现/消失时，下方 `el-table` 的位置每帧重算。而且它只平滑了 12px，栏本身的高度仍然瞬时占用——等于付出了重排代价，却没有换来真正的顺滑。

`frontend/src/views/admin/SpuMgmt.vue:1098-1100`
```css
.batch-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 10px 16px; background: var(--brand-primary-soft); border: 1px solid var(--brand-primary); border-radius: var(--radius-md); overflow: hidden; }
.toolbar-slide-enter-active, .toolbar-slide-leave-active { transition: opacity 0.15s var(--ease-out), transform 0.15s var(--ease-out), margin-bottom 0.15s var(--ease-out); }
.toolbar-slide-enter-from, .toolbar-slide-leave-to { opacity: 0; transform: translateY(-6px); margin-bottom: 0; }
```

## Target

过渡只保留合成友好的 `opacity` 与 `transform`：

```css
.toolbar-slide-enter-active, .toolbar-slide-leave-active { transition: opacity 0.15s var(--ease-out), transform 0.15s var(--ease-out); }
.toolbar-slide-enter-from, .toolbar-slide-leave-to { opacity: 0; transform: translateY(-6px); }
```

已知取舍（不要试图在本方案里额外解决）：批量栏进入文档流时，它自身高度造成的位移仍是瞬时的。要彻底消除需要把工具栏改为覆盖层或预留高度，属于版式改动，不在本方案范围。

## Repo conventions to follow

- 只动 transform/opacity：参考 `main.scss:426`（`.hover-lift` 只过渡 transform 与 box-shadow）。
- 0.15s 已是库内既有值，保持不变，仅删除属性。

## Steps

1. 修改 `frontend/src/views/admin/SpuMgmt.vue:1099`，删除 `, margin-bottom 0.15s var(--ease-out)`。
2. 修改同文件 `:1100`，删除 `margin-bottom: 0;`。

## Boundaries

- 不要改 `.batch-toolbar` 的 `margin-bottom: 12px` 静态值。
- 不要改进场位移（-6px）与时长（0.15s）。
- 不要动本页其它过渡（`:1158`、`:1192`）。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - 进入商品管理，勾选任意一行：批量栏应从上方滑入淡入，表格随之下移一次（瞬时），**不应看到表格逐帧缓慢下滑**。
  - 取消全选：批量栏滑出淡出，表格上移一次。
  - Performance 面板录制，确认勾选动作的动画帧里没有 Layout / Paint 抖动（只应有 Composite）。
- **Done when**: 批量栏进出只走 transform/opacity，过渡列表里不再含任何布局属性。
