# 007 — 聊天消息入场收到 240ms

- **Status**: TODO
- **Commit**: d5910e2
- **Severity**: LOW
- **Category**: 2 缓动与时长
- **Estimated scope**: 1 文件 / 1 行

## Problem

每条消息行入场动画是 350ms，超出 UI 动效 300ms 预算。聊天页消息是连续出现的，偏长的入场会让列表下落显得黏、并且多条消息接连到达时视觉上会互相拖拽。

`frontend/src/components/ChatMessage.vue:223-238`
```css
.msg-row {
  display: flex;
  gap: 14px;
  padding: 20px 0;
  animation: msgFadeIn 0.35s ease-out;
  max-width: 100%;

  &:last-child {
    padding-bottom: 8px;
  }
}

@keyframes msgFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
```

## Target

```css
  animation: msgFadeIn 0.24s var(--ease-out);
```

位移距离（8px）与 keyframes 保持不变。改时长即可，曲线从裸 `ease-out` 换成 token，值同为强 ease-out。

## Repo conventions to follow

- 消息 key 由 `:key="m.id"` 固定（`RagChat.vue:143`），流式追加 token 不会重播该动画——不要改成 `transition`，keyframes 在这里是正确的。
- 时长预算：UI 元素 ≤300ms，本项取 240ms 与列表入场节奏一致。

## Steps

1. 修改 `frontend/src/components/ChatMessage.vue:227`，把 `animation: msgFadeIn 0.35s ease-out;` 改为 `animation: msgFadeIn 0.24s var(--ease-out);`。

## Boundaries

- 不要改 `@keyframes msgFadeIn` 的位移距离。
- 不要动 `RagChat.vue:1169` 的 `.thinking` 入场（0.3s，属于思考态提示，另有语义）。
- 不要给消息加 stagger 延迟。

## Verification

- **Mechanical**: `cd frontend && npm run build`。
- **Feel check**:
  - 在 AI 助手连续发 3 条消息：每条应从下方 8px 淡入，整体下落节奏紧凑、不拖尾。
  - 流式回答过程中，已出现的那条消息**不应重播**淡入（应只有光标闪动和文字增长）。
  - DevTools Animations 10% 速度确认时长约为 0.24s。
- **Done when**: 消息入场时长为 240ms 且曲线走 token。
