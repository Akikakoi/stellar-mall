# 动效改进方案索引

审计基准 commit：`d5910e2`。审计标准：Emil Kowalski 动效原则（目的与频率、缓动与时长、物理性与原点、可中断性、性能、无障碍、一致性、遗漏机会）。

项目现状（无需改进的部分，执行时不要动）：`--ease-out` / `--ease-in-out` / `--transition-base` / `--transition-color` 四档 token 已建立；全局 `prefers-reduced-motion` 兜底完整；14 处 hover 位移已做 `(hover: hover)` 门控；拖拽排序 FLIP 显式尊重减弱动效；全仓无 `transition: all`、无 `scale(0)`。

## 方案列表

| # | 方案 | 级别 | 类目 | 状态 |
| --- | --- | --- | --- | --- |
| 001 | [触屏 hover 位移补上门控](001-touch-hover-gating.md) | HIGH | 6 无障碍 | DONE |
| 002 | [给点击目标补按压反馈](002-press-feedback.md) | HIGH | 3 物理性 | DONE |
| 003 | [管理端侧栏折叠不再动画 width](003-admin-sidebar-width.md) | MEDIUM | 5 性能 | DONE |
| 004 | [批量操作栏不再动画 margin-bottom](004-batch-toolbar-margin.md) | MEDIUM | 5 性能 | DONE |
| 005 | [弹窗离场改用 ease-out](005-dialog-exit-easing.md) | MEDIUM | 2 缓动 | DONE |
| 006 | [手写曲线收敛到缓动 token](006-easing-token-consolidation.md) | MEDIUM | 7 一致性 | DONE |
| 007 | [聊天消息入场收到 240ms](007-chat-message-entrance-duration.md) | LOW | 2 缓动 | DONE |
| 008 | [三处 grid 展开过渡合并为统一类](008-expand-transition-consolidation.md) | LOW | 5 性能 / 7 一致性 | DONE |
| 009 | [骨架屏 shimmer 补 linear](009-skeleton-shimmer-linear.md) | LOW | 7 一致性 | DONE |
| 010 | [图片放大预览的入场缓动与物理感](010-lightbox-easing.md) | LOW | 2 缓动 / 3 物理性 | DONE |
| 011 | [裸时长收敛到过渡 token](011-raw-duration-tokenization.md) | LOW | 7 一致性 | DONE |
| 012 | [AI 助手品牌图标去掉回弹旋转](012-ragchat-brand-icon-hover.md) | LOW | 7 一致性 | DONE |

## 建议执行顺序

1. **手感阶段**：001 → 002 → 004 → 003
2. **体系阶段**：005 → 006 → 011
3. **打磨阶段**：007 → 008 → 009 → 010 → 012

理由：先解决触屏误态与缺失的按压确认（用户可感知度最高），再处理两个布局属性动画；随后统一曲线与时长，为后续任何动效改动立好护栏；最后处理单点打磨与去重。

## 依赖与冲突

- **001 与 012**：都改 `RagChat.vue` 的 hover，但对象不同（`.prompt` vs `.brand`）。012 在 001 之后执行，避免同一文件两次改动互相覆盖。
- **002 与 001**：002 添加的 `.spu-card:active` 会覆盖 hover 的 `translateY`，先完成 001 的门控再执行 002，触屏按压表现才正确。
- **006 与 011**：无硬依赖，但建议 006 先做（曲线先统一），011 只处理时长，避免同一行改两次。
- **005 与 006**：都改 `main.scss` 的弹窗动画段，005 改 timing-function（`:526/533/547/550`），006 改 `:204` 与 `:541`，行号不重叠，可并行，但按 005 → 006 顺序更稳。
- **008** 改动三个组件的 `<transition name>`，与其它方案无交集。
- **003** 与 **004** 分属不同文件，可并行。

## 验收基线（全部完成后）

- 触屏点按任何卡片不留 hover 残留，且有 160ms 按压反馈。
- 全仓 `grep -rn "cubic-bezier(0.4, 0, 0.2, 1)\|cubic-bezier(0.34, 1.56" frontend/src` 无结果。
- 动画帧内不出现 width / margin / height 过渡（003、004 完成后）。
- 开启 `prefers-reduced-motion` 时所有位移动画消失，颜色与透明度反馈保留。
