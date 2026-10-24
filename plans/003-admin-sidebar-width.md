# 003 — 管理端侧栏折叠不再动画 width

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: MEDIUM
- **Category**: 5 性能
- **Estimated scope**: 1 文件 / 1 行删除

## Problem

管理端侧栏用 `width` 过渡实现折叠。宽度是布局属性，200ms 内每一帧都会让右侧整个 `el-main`（含 el-table、ECharts 容器、分页）重新计算布局，低端机折叠时明显掉帧。侧栏折叠是结构性切换、每次会话只用几次，用一次整页重排换 200ms 的"顺滑"不划算。

`frontend/src/views/admin/_Layout.vue:3`
```html
<el-aside :width="collapsed ? '64px' : '220px'" class="sidebar">
```

`frontend/src/views/admin/_Layout.vue:190-195`（当前）
```css
.sidebar {
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-base);
  transition: width var(--transition-base);
  overflow: hidden;
}
```

## Target

删掉 `transition: width`，让 220px ↔ 64px 瞬时切换。菜单项文字本身由 `v-if="!collapsed"` 控制（`_Layout.vue:10`），已经不存在需要淡出的内容。

```css
.sidebar {
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-base);
  /* 折叠是结构性切换：宽度瞬切，避免 200ms 内反复重排右侧表格/图表 */
  overflow: hidden;
}
```

## Repo conventions to follow

- 结构性（layout）变化不做补间是本项目既有取舍：`_Layout.vue:15` 的侧栏菜单已显式 `:collapse-transition="false"`，与本方案同一原则。
- 不要引入新的"折叠动画"替代品（例如给 `.main-content` 加 margin 过渡），那只是把重排换个位置。

## Steps

1. 删除 `frontend/src/views/admin/_Layout.vue:193` 的 `transition: width var(--transition-base);`。
2. 在原位置补上面那行中文注释。
3. 确认 `el-aside` 的 `:width` 绑定保持不变（瞬时切换依赖它）。

## Boundaries

- 只改 `.sidebar` 这一个规则；不要动 `.main-content`、菜单项、`collapsed` 逻辑。
- 不要改任何模板结构或宽度数值。

## Verification

- **Mechanical**: `cd frontend && npm run typecheck && npm run build`。
- **Feel check**:
  - 打开 `/admin/dashboard`（含图表与表格），点折叠按钮：侧栏应瞬间变窄，右侧内容同时到位，**不应看到表格列宽重排的拖影**。
  - Performance 面板录制折叠操作，确认 Layout 事件不出现在动画帧里（应为一次性 layout）。
- **Done when**: 折叠无 width 过渡，右侧内容不出现逐帧重排。
