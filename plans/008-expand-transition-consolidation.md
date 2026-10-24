# 008 — 三处 grid 展开过渡合并为统一类

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: LOW
- **Category**: 5 性能 / 7 一致性
- **Estimated scope**: 4 文件（3 个组件 + `main.scss`）

## Problem

`grid-template-rows: 0fr ↔ 1fr` 的自适应高度展开被复制到了三处，参数几乎相同（0.25s / 0.2s），且都是布局属性动画。三份拷贝导致以后调一次时长要改三遍，也容易出现"有的 250ms、有的 200ms"的漂移。

`frontend/src/components/SkuSpecSelector.vue:599-608`
```css
.service-expand-enter-active,
.service-expand-leave-active {
  transition: grid-template-rows 0.25s var(--ease-out), opacity 0.2s var(--ease-out);
}

.service-expand-enter-from,
.service-expand-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}
```

`frontend/src/components/SourceCard.vue:239-248`
```css
.content-expand-enter-active,
.content-expand-leave-active {
  transition: grid-template-rows 0.25s var(--ease-out), opacity 0.2s var(--ease-out);
}

.content-expand-enter-from,
.content-expand-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}
```

`frontend/src/components/ChatMessage.vue:437-446`
```css
.sources-expand-enter-active,
.sources-expand-leave-active {
  transition: grid-template-rows 0.25s var(--ease-out), opacity 0.2s var(--ease-out);
}

.sources-expand-enter-from,
.sources-expand-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}
```

对应模板：`SkuSpecSelector.vue:79` `<transition name="service-expand">`、`SourceCard.vue:38` `<transition name="content-expand">`、`ChatMessage.vue:46` `<transition name="sources-expand">`。

## Target

三处统一改用全局过渡名 `expand`，实现只留一份，放在 `frontend/src/assets/main.scss`（`.hover-lift` 之后）：

```css
/* ===== 自适应高度展开/收起（grid-template-rows 0fr↔1fr）=====
   统一用于：SKU 服务说明、AI 引用来源、来源卡片正文。
   时长取 200ms（原 250ms 收缩到 UI 预算内）。 */
.expand-enter-active,
.expand-leave-active {
  transition: grid-template-rows 200ms var(--ease-out), opacity 200ms var(--ease-out);
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}
```

模板改为 `name="expand"`；三个组件里对应的 3 组 scoped 规则整段删除。

## Repo conventions to follow

- 全局过渡类放 `main.scss`（与 `.dialog-fade-*` 覆盖 Element Plus 动画同一位置逻辑）。
- 外层元素仍需保留各自的 `display: grid; grid-template-rows: 1fr; min-height: 0;`（`SkuSpecSelector.vue:584-588`、`SourceCard.vue:227-231`、`ChatMessage.vue:425-429`），本方案不动。

## Steps

1. 在 `frontend/src/assets/main.scss` 的 `.hover-lift` 块之后插入上面的全局 `expand` 过渡。
2. `frontend/src/components/SkuSpecSelector.vue:79`：`name="service-expand"` → `name="expand"`；删除 `:599-608` 整段。
3. `frontend/src/components/SourceCard.vue:38`：`name="content-expand"` → `name="expand"`；删除 `:239-248` 整段。
4. `frontend/src/components/ChatMessage.vue:46`：`name="sources-expand"` → `name="expand"`；删除 `:437-446` 整段。

## Boundaries

- 不要换掉 `grid-template-rows` 方案去改 `max-height`（组件里的注释已说明为什么淘汰 max-height）。
- 不要动展开触发逻辑、`v-show`/`v-if`、箭头旋转规则。
- 不要新增依赖。

## Verification

- **Mechanical**: `cd frontend && npm run typecheck && npm run build`；全仓搜索 `service-expand|content-expand|sources-expand` 应无结果。
- **Feel check**:
  - 商品详情展开"服务说明"、AI 回答展开"引用来源"、展开单张来源卡片正文：三者展开/收起节奏应一致，约 200ms。
  - 快速连点同一个展开按钮：应从中途**反向**回退，而不是从 0 重播（transition 具备可中断性）。
  - 开启 `prefers-reduced-motion`：展开应瞬时到位且内容可见。
- **Done when**: 三处共用一份 `expand` 过渡，行为与时长一致。
